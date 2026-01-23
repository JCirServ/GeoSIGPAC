
package com.geosigpac.cirserv.services

import android.util.Log
import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.model.SigpacData
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object SigpacApiService {

    private const val TAG = "SigpacApiService"

    suspend fun fetchHydration(referencia: String): Triple<SigpacData?, CultivoData?, Pair<Double, Double>?> = withContext(Dispatchers.IO) {
        val parts = referencia.split(":", "-").filter { it.isNotBlank() }
        
        val prov = parts.getOrNull(0) ?: ""
        val mun = parts.getOrNull(1) ?: ""
        val hasCompleteFormat = parts.size >= 7
        
        val ag = if (hasCompleteFormat) parts[2] else "0"
        val zo = if (hasCompleteFormat) parts[3] else "0"
        val pol = if (hasCompleteFormat) parts[4] else (parts.getOrNull(parts.size - 3) ?: "")
        val parc = if (hasCompleteFormat) parts[5] else (parts.getOrNull(parts.size - 2) ?: "")
        val rec = if (hasCompleteFormat) parts[6] else (parts.getOrNull(parts.size - 1) ?: "")

        // 1. CONSULTA RECINTO (JSON DETALLADO)
        val recintoUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfo/$prov/$mun/$ag/$zo/$pol/$parc/$rec.json"
        
        // 2. CONSULTA CULTIVO DECLARADO (OGC API)
        val ogcQuery = "provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        val cultivoUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?$ogcQuery"

        // 3. CENTROIDE: DESACTIVADO (Usamos KML)
        val centroid: Pair<Double, Double>? = null

        val sigpac = fetchUrl(recintoUrl)?.let { jsonStr ->
            try {
                val array = JSONArray(jsonStr)
                if (array.length() > 0) {
                    val props = array.getJSONObject(0)
                    val rawUso = props.optString("uso_sigpac")
                    val translatedUso = SigpacCodeManager.getUsoDescription(rawUso)
                    val rawRegion = props.optString("region")
                    val translatedRegion = SigpacCodeManager.getRegionDescription(rawRegion)

                    SigpacData(
                        superficie = if (props.isNull("superficie")) null else props.optDouble("superficie"),
                        pendienteMedia = if (props.isNull("pendiente_media")) null else props.optDouble("pendiente_media"),
                        coefRegadio = if (props.isNull("coef_regadio")) null else props.optDouble("coef_regadio"),
                        admisibilidad = if (props.isNull("admisibilidad")) null else props.optDouble("admisibilidad"),
                        incidencias = props.optString("incidencias")?.replace("[", "")?.replace("]", "")?.replace("\"", ""),
                        usoSigpac = translatedUso,
                        region = translatedRegion,
                        altitud = if (props.isNull("altitud")) null else props.optInt("altitud")
                    )
                } else null
            } catch (e: Exception) { null }
        }

        val cultivo = fetchUrl(cultivoUrl)?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    val props = features.getJSONObject(0).getJSONObject("properties")
                    val rawAprovecha = props.optString("tipo_aprovecha")
                    val translatedAprovecha = SigpacCodeManager.getAprovechamientoDescription(rawAprovecha)

                    CultivoData(
                        expNum = props.optString("exp_num"),
                        parcProducto = if (props.isNull("parc_producto")) null else props.optInt("parc_producto"),
                        parcSistexp = props.optString("parc_sistexp"),
                        parcSupcult = if (props.isNull("parc_supcult")) null else props.optDouble("parc_supcult"),
                        parcAyudasol = props.optString("parc_ayudasol"),
                        pdrRec = props.optString("pdr_rec"),
                        cultsecunProducto = if (props.isNull("cultsecun_producto")) null else props.optInt("cultsecun_producto"),
                        cultsecunAyudasol = props.optString("cultsecun_ayudasol"),
                        parcIndcultapro = if (props.isNull("parc_indcultapro")) null else props.optInt("parc_indcultapro"),
                        tipoAprovecha = translatedAprovecha
                    )
                } else null
            } catch (e: Exception) { null }
        }

        Triple(sigpac, cultivo, centroid)
    }

    /**
     * Recupera la información completa de una parcela (Ref, Geometría, Datos) a partir de un punto Lat/Lng.
     * Prioriza la geometría de Cultivo Declarado. Si no existe, usa la de Recinto.
     */
    suspend fun recoverParcelaFromPoint(lat: Double, lng: Double): Triple<String?, String?, SigpacData?> = withContext(Dispatchers.IO) {
        // 1. OBTENER REFERENCIA (Reverse Geocoding)
        val identifyUrl = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val idResponse = fetchUrl(identifyUrl) ?: return@withContext Triple(null, null, null)

        var prov = ""; var mun = ""; var pol = ""; var parc = ""; var rec = ""; var agg = "0"; var zon = "0"
        var sigpacData: SigpacData? = null

        try {
            val jsonArray = JSONArray(idResponse)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                prov = obj.optString("provincia"); mun = obj.optString("municipio")
                agg = obj.optString("agregado", "0"); zon = obj.optString("zona", "0")
                pol = obj.optString("poligono"); parc = obj.optString("parcela"); rec = obj.optString("recinto")

                val translatedUso = SigpacCodeManager.getUsoDescription(obj.optString("uso_sigpac"))
                val translatedRegion = SigpacCodeManager.getRegionDescription(obj.optString("region"))
                
                sigpacData = SigpacData(
                    superficie = obj.optDouble("superficie"),
                    pendienteMedia = obj.optDouble("pendiente_media"),
                    coefRegadio = obj.optDouble("coef_regadio"),
                    admisibilidad = obj.optDouble("coef_admisibilidad_pastos"), // Nota: la clave cambia a veces
                    usoSigpac = translatedUso,
                    region = translatedRegion,
                    altitud = obj.optInt("altitud")
                )
            }
        } catch (e: Exception) { return@withContext Triple(null, null, null) }

        if (prov.isEmpty()) return@withContext Triple(null, null, null)

        val fullRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
        var geometryRaw: String? = null

        // 2. INTENTAR GEOMETRÍA DE CULTIVO (OGC API)
        val ogcUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        val cultivoJson = fetchUrl(ogcUrl)
        
        if (cultivoJson != null) {
            geometryRaw = extractGeometryFromGeoJson(cultivoJson)
        }

        // 3. SI NO HAY CULTIVO, INTENTAR GEOMETRÍA DE RECINTO
        if (geometryRaw == null) {
            val recUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/$prov/$mun/$agg/$zon/$pol/$parc/$rec.geojson"
            val recJson = fetchUrl(recUrl)
            if (recJson != null) {
                geometryRaw = extractGeometryFromGeoJson(recJson)
            }
        }

        return@withContext Triple(fullRef, geometryRaw, sigpacData)
    }

    private fun extractGeometryFromGeoJson(jsonStr: String): String? {
        return try {
            val root = JSONNative(jsonStr)
            var geometry: JSONNative? = null
            
            // Caso FeatureCollection
            if (root.has("features")) {
                val features = root.getJSONArray("features")
                if (features.length() > 0) {
                    geometry = features.getJSONObject(0).optJSONObject("geometry")
                }
            } else if (root.has("geometry")) {
                // Caso Feature simple
                geometry = root.optJSONObject("geometry")
            }

            if (geometry != null) {
                val coords = geometry.getJSONArray("coordinates")
                // Aplanar coordenadas para formato simple "lng,lat lng,lat ..."
                val sb = StringBuilder()
                flattenCoordinates(coords, sb)
                sb.toString().trim()
            } else null
        } catch (e: Exception) { null }
    }

    private fun flattenCoordinates(arr: JSONArray, sb: StringBuilder) {
        if (arr.length() == 0) return
        val first = arr.get(0)
        if (first is Number) {
            // Punto [lng, lat]
            val lng = arr.getDouble(0)
            val lat = arr.getDouble(1)
            sb.append("$lng,$lat ")
        } else if (first is JSONArray) {
            for (i in 0 until arr.length()) {
                flattenCoordinates(arr.getJSONArray(i), sb)
            }
        }
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "GeoSIGPAC-Mobile/1.0")
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }
}
