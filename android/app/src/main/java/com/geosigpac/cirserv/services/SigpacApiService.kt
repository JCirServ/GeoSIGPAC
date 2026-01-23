
package com.geosigpac.cirserv.services

import android.util.Log
import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.model.SigpacData
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object SigpacApiService {

    private const val TAG = "SigpacApiService"

    /**
     * @param targetAreaHa Superficie CALCULADA del polígono KML en Hectáreas (para desempate).
     * @param pointCheck Coordenadas (lat, lng) para verificar si el punto cae dentro del cultivo (prioridad máxima).
     */
    suspend fun fetchHydration(
        referencia: String, 
        targetAreaHa: Double? = null,
        pointCheck: Pair<Double, Double>? = null
    ): Triple<SigpacData?, CultivoData?, Pair<Double, Double>?> = withContext(Dispatchers.IO) {
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

        // 3. CENTROIDE: DESACTIVADO (Usamos KML local)
        val centroid: Pair<Double, Double>? = null

        val sigpac = fetchUrl(recintoUrl)?.let { parseSigpacDataJson(it) }

        val cultivo = fetchUrl(cultivoUrl)?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                
                if (features != null && features.length() > 0) {
                    val allDeclarations = mutableListOf<JSONObject>()
                    for (i in 0 until features.length()) {
                        allDeclarations.add(features.getJSONObject(i))
                    }

                    // Filtrar por el AÑO MÁS RECIENTE disponible en los datos
                    val maxYear = allDeclarations.maxOfOrNull { it.optJSONObject("properties")?.optInt("exp_ano", 0) ?: 0 } ?: 0
                    val currentYearFeatures = allDeclarations.filter { it.optJSONObject("properties")?.optInt("exp_ano", 0) == maxYear }

                    var bestFeature: JSONObject? = null
                    
                    // A. Prioridad: Coincidencia por PUNTO (Para marcadores KML convertidos)
                    if (pointCheck != null) {
                        for (feat in currentYearFeatures) {
                            val geom = feat.optJSONObject("geometry")
                            if (geom != null && isPointInGeoJsonGeometry(pointCheck.first, pointCheck.second, geom)) {
                                bestFeature = feat
                                break
                            }
                        }
                    }

                    // B. Prioridad: Coincidencia por ÁREA (Para polígonos KML)
                    // Si hay varios cultivos en un recinto, buscamos el que tenga un área similar a la dibujada en KML
                    if (bestFeature == null && targetAreaHa != null && targetAreaHa > 0) {
                        val toleranceSteps = listOf(0.005, 0.01, 0.05, 0.10) // Tolerancia en Ha
                        
                        for (tol in toleranceSteps) {
                            var bestDiff = Double.MAX_VALUE
                            
                            for (feat in currentYearFeatures) {
                                val props = feat.optJSONObject("properties") ?: continue
                                val supM2 = props.optDouble("parc_supcult", 0.0)
                                val supHa = supM2 / 10000.0
                                val diff = abs(supHa - targetAreaHa)
                                
                                if (diff <= tol && diff < bestDiff) {
                                    bestDiff = diff
                                    bestFeature = feat
                                }
                            }
                            if (bestFeature != null) break 
                        }
                    }

                    // C. Fallback: Cultivo Principal (Mayor superficie)
                    if (bestFeature == null && currentYearFeatures.isNotEmpty()) {
                        bestFeature = currentYearFeatures.maxByOrNull { it.optJSONObject("properties")?.optDouble("parc_supcult", 0.0) ?: 0.0 }
                    }

                    if (bestFeature != null) {
                        val props = bestFeature.optJSONObject("properties")
                        if (props != null) {
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
                    } else null
                } else null
            } catch (e: Exception) { null }
        }

        Triple(sigpac, cultivo, centroid)
    }

    suspend fun recoverParcelaFromPoint(lat: Double, lng: Double): Triple<String?, String?, SigpacData?> = withContext(Dispatchers.IO) {
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
                    admisibilidad = obj.optDouble("coef_admisibilidad_pastos"),
                    usoSigpac = translatedUso,
                    region = translatedRegion,
                    altitud = obj.optInt("altitud")
                )
            }
        } catch (e: Exception) { return@withContext Triple(null, null, null) }

        if (prov.isEmpty()) return@withContext Triple(null, null, null)

        val fullRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
        var geometryRaw: String? = null

        // GEOMETRÍA: Intentamos obtener la geometría del cultivo que está justo en el punto
        val ogcUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        val cultivoJson = fetchUrl(ogcUrl)
        
        if (cultivoJson != null) {
            try {
                val root = JSONNative(cultivoJson)
                val features = root.optJSONArray("features")
                if (features != null) {
                    for (i in 0 until features.length()) {
                        val feat = features.getJSONObject(i)
                        val geom = feat.optJSONObject("geometry")
                        // Si el punto cae en esta geometría específica de cultivo, la usamos
                        if (geom != null && isPointInGeoJsonGeometry(lat, lng, geom)) {
                            geometryRaw = extractGeometryFromGeoJsonObj(geom)
                            break
                        }
                    }
                    // Si no encontramos uno específico por punto, cogemos el primero (fallback)
                    if (geometryRaw == null && features.length() > 0) {
                         geometryRaw = extractGeometryFromGeoJsonObj(features.getJSONObject(0).optJSONObject("geometry"))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // FALLBACK: Geometría de Recinto
        if (geometryRaw == null) {
            val recUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/$prov/$mun/$agg/$zon/$pol/$parc/$rec.geojson"
            val recJson = fetchUrl(recUrl)
            if (recJson != null) {
                geometryRaw = extractGeometryFromGeoJson(recJson)
            }
        }

        return@withContext Triple(fullRef, geometryRaw, sigpacData)
    }

    private fun parseSigpacDataJson(jsonStr: String): SigpacData? {
        try {
            val array = JSONArray(jsonStr)
            if (array.length() > 0) {
                val props = array.getJSONObject(0)
                val rawUso = props.optString("uso_sigpac")
                val translatedUso = SigpacCodeManager.getUsoDescription(rawUso)
                val rawRegion = props.optString("region")
                val translatedRegion = SigpacCodeManager.getRegionDescription(rawRegion)
                return SigpacData(
                    superficie = if (props.isNull("superficie")) null else props.optDouble("superficie"),
                    pendienteMedia = if (props.isNull("pendiente_media")) null else props.optDouble("pendiente_media"),
                    coefRegadio = if (props.isNull("coef_regadio")) null else props.optDouble("coef_regadio"),
                    admisibilidad = if (props.isNull("admisibilidad")) null else props.optDouble("admisibilidad"),
                    incidencias = props.optString("incidencias")?.replace("[", "")?.replace("]", "")?.replace("\"", ""),
                    usoSigpac = translatedUso,
                    region = translatedRegion,
                    altitud = if (props.isNull("altitud")) null else props.optInt("altitud")
                )
            }
        } catch (e: Exception) { Log.e(TAG, "Error parsing SigpacData JSON: ${e.message}") }
        return null
    }

    private fun extractGeometryFromGeoJsonObj(geometry: JSONObject?): String? {
        // CORRECCIÓN CLAVE: Devolvemos el JSON String completo para mantener polígonos complejos/multipolígonos válidos.
        // MapLibre puede parsear Geometry.fromJson() directamente.
        return geometry?.toString()
    }

    private fun extractGeometryFromGeoJson(jsonStr: String): String? {
        return try {
            val root = JSONNative(jsonStr)
            var geometry: JSONNative? = null
            if (root.has("features")) {
                val features = root.getJSONArray("features")
                if (features.length() > 0) {
                    geometry = features.getJSONObject(0).optJSONObject("geometry")
                }
            } else if (root.has("geometry")) {
                geometry = root.optJSONObject("geometry")
            }
            extractGeometryFromGeoJsonObj(geometry)
        } catch (e: Exception) { null }
    }

    private fun isPointInGeoJsonGeometry(lat: Double, lng: Double, geometry: JSONObject): Boolean {
        return try {
            val type = geometry.optString("type")
            val coordinates = geometry.getJSONArray("coordinates")
            
            if (type.equals("Polygon", ignoreCase = true)) {
                if (coordinates.length() > 0) {
                    val ring = parseRing(coordinates.getJSONArray(0))
                    return isPointInPolygon(lat, lng, ring)
                }
            } else if (type.equals("MultiPolygon", ignoreCase = true)) {
                for (i in 0 until coordinates.length()) {
                    val poly = coordinates.getJSONArray(i)
                    if (poly.length() > 0) {
                        val ring = parseRing(poly.getJSONArray(0))
                        if (isPointInPolygon(lat, lng, ring)) return true
                    }
                }
            }
            false
        } catch (e: Exception) { false }
    }

    private fun parseRing(jsonRing: JSONArray): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until jsonRing.length()) {
            val pt = jsonRing.getJSONArray(i)
            val pLng = pt.getDouble(0)
            val pLat = pt.getDouble(1)
            list.add(pLat to pLng)
        }
        return list
    }

    private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]
            if (((latI > lat) != (latJ > lat)) &&
                (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun fetchUrl(urlString: String): String? {
        Log.d(TAG, "HTTP GET REQ: $urlString")
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "GeoSIGPAC-Mobile/1.0")
            
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "HTTP Error Code: $code")
                null
            }
        } catch (e: Exception) { 
            Log.e(TAG, "HTTP Exception: ${e.message}")
            null 
        }
    }
}
