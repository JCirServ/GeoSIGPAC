
package com.geosigpac.cirserv.services

import android.util.Log
import com.geosigpac.cirserv.model.CultivoData
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

    private const val TAG = "SigpacDebug" // Etiqueta para filtrar en Logcat

    /**
     * @param targetAreaHa Superficie CALCULADA del polígono KML en Hectáreas.
     * @param pointCheck Coordenadas (lat, lng) para verificar si el punto cae dentro del cultivo (prioridad sobre área).
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

        // 3. CENTROIDE: DESACTIVADO (Usamos KML)
        val centroid: Pair<Double, Double>? = null

        val sigpac = fetchUrl(recintoUrl)?.let { parseSigpacDataJson(it) }

        val cultivo = fetchUrl(cultivoUrl)?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                
                if (features != null && features.length() > 0) {
                    val allDeclarations = mutableListOf<JSONObject>()
                    for (i in 0 until features.length()) {
                        allDeclarations.add(features.getJSONObject(i)) // Guardamos la Feature completa para acceso a geometry
                    }

                    // Filtrar por el AÑO MÁS RECIENTE
                    val maxYear = allDeclarations.maxOfOrNull { it.optJSONObject("properties")?.optInt("exp_ano", 0) ?: 0 } ?: 0
                    val currentYearFeatures = allDeclarations.filter { it.optJSONObject("properties")?.optInt("exp_ano", 0) == maxYear }

                    var bestFeature: JSONObject? = null
                    
                    // A. Prioridad: Coincidencia por PUNTO (Para marcadores KML)
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
                    if (bestFeature == null && targetAreaHa != null && targetAreaHa > 0) {
                        val toleranceSteps = listOf(0.001, 0.005, 0.01, 0.02, 0.05, 0.10)
                        
                        for (tol in toleranceSteps) {
                            val candidates = mutableListOf<Pair<JSONObject, Double>>()
                            
                            for (feat in currentYearFeatures) {
                                val props = feat.optJSONObject("properties") ?: continue
                                val supM2 = props.optDouble("parc_supcult", 0.0)
                                val supHa = supM2 / 10000.0
                                
                                val diffRaw = abs(supHa - targetAreaHa)
                                val targetRounded = (targetAreaHa * 100.0).roundToInt() / 100.0
                                val supRounded = (supHa * 100.0).roundToInt() / 100.0
                                val diffRounded = abs(supRounded - targetRounded)
                                val effectiveMetric = if (diffRounded < 0.001) 0.0 else diffRaw
                                
                                if (effectiveMetric <= tol) {
                                    candidates.add(feat to diffRaw)
                                }
                            }
                            
                            if (candidates.isNotEmpty()) {
                                bestFeature = candidates.minByOrNull { it.second }?.first
                                break 
                            }
                        }
                    }

                    // C. Fallback: Cultivo Principal (Mayor superficie) si no hay criterios o fallaron
                    if (bestFeature == null && currentYearFeatures.isNotEmpty() && pointCheck == null) {
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
        // 1. OBTENER REFERENCIA POR COORDENADAS (API REFRECINBYCOORD)
        val refUrl = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/refrecinbycoord/4326/%.8f/%.8f.geojson", lng, lat)
        
        Log.d(TAG, "STEP 1: Requesting Ref by Point: $refUrl")
        val refResponse = fetchUrl(refUrl)
        
        if (refResponse == null) {
            Log.e(TAG, "STEP 1 FAILED: Response was null (Connection error?)")
            return@withContext Triple(null, null, null)
        }
        
        Log.d(TAG, "STEP 1 RAW JSON: $refResponse")

        var prov = ""; var mun = ""; var pol = ""; var parc = ""; var rec = ""; var agg = "0"; var zon = "0"
        
        try {
            val root = JSONObject(refResponse)
            var props: JSONObject? = null
            
            val features = root.optJSONArray("features")
            if (features != null && features.length() > 0) {
                props = features.getJSONObject(0).optJSONObject("properties")
            } else {
                props = root.optJSONObject("properties") ?: root
            }

            if (props != null) {
                prov = props.optString("provincia")
                mun = props.optString("municipio")
                agg = props.optString("agregado", "0")
                zon = props.optString("zona", "0")
                pol = props.optString("poligono")
                parc = props.optString("parcela")
                rec = props.optString("recinto")
                Log.d(TAG, "Parsed Location: P:$prov M:$mun Pol:$pol Parc:$parc Rec:$rec")
            } else {
                Log.e(TAG, "Parsed Location: Properties not found in JSON (Empty features?)")
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Exception parsing RefByPoint: ${e.message}")
            e.printStackTrace()
            return@withContext Triple(null, null, null) 
        }

        if (prov.isEmpty() || mun.isEmpty()) {
            Log.e(TAG, "Invalid location data parsed: prov=$prov, mun=$mun. Aborting.")
            return@withContext Triple(null, null, null)
        }
        
        val fullRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
        Log.d(TAG, "Full Reference Constructed: $fullRef")

        // 2. OBTENER DATOS SIGPAC (RECINFO)
        val recInfoUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfo/$prov/$mun/$agg/$zon/$pol/$parc/$rec.json"
        Log.d(TAG, "STEP 2: Requesting RecInfo: $recInfoUrl")
        val sigpacData = fetchUrl(recInfoUrl)?.let { parseSigpacDataJson(it) }

        // 3. OBTENER GEOMETRÍA CULTIVO ESPECÍFICO (SI EL PUNTO CAE DENTRO)
        var geometryRaw: String? = null
        val ogcUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        Log.d(TAG, "STEP 3: Requesting Cultivo Geom: $ogcUrl")
        val cultivoJson = fetchUrl(ogcUrl)
        
        if (cultivoJson != null) {
            try {
                val root = JSONNative(cultivoJson)
                val features = root.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    val allDeclarations = mutableListOf<JSONObject>()
                    for (i in 0 until features.length()) allDeclarations.add(features.getJSONObject(i))
                    
                    val maxYear = allDeclarations.maxOfOrNull { it.optJSONObject("properties")?.optInt("exp_ano", 0) ?: 0 } ?: 0
                    val currentYearFeatures = allDeclarations.filter { it.optJSONObject("properties")?.optInt("exp_ano", 0) == maxYear }
                    
                    // Buscar el cultivo geométricamente
                    for (feat in currentYearFeatures) {
                        val geom = feat.optJSONObject("geometry")
                        if (geom != null && isPointInGeoJsonGeometry(lat, lng, geom)) {
                            geometryRaw = extractGeometryFromGeoJsonObj(geom)
                            Log.d(TAG, "Found Geometry in Cultivo (Year $maxYear)")
                            break
                        }
                    }
                }
            } catch (e: Exception) { 
                 Log.e(TAG, "Error parsing cultivo geom: ${e.message}")
            }
        }

        // 4. FALLBACK: GEOMETRÍA DEL RECINTO
        if (geometryRaw == null) {
            val recGeomUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/$prov/$mun/$agg/$zon/$pol/$parc/$rec.geojson"
            Log.d(TAG, "STEP 4: Fallback to Recinto Geom: $recGeomUrl")
            val recGeomJson = fetchUrl(recGeomUrl)
            if (recGeomJson != null) {
                 geometryRaw = extractGeometryFromGeoJson(recGeomJson)
                 if (geometryRaw != null) Log.d(TAG, "Recinto Geom Found")
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
        if (geometry == null) return null
        return try {
            val coords = geometry.getJSONArray("coordinates")
            val sb = StringBuilder()
            flattenCoordinates(coords, sb)
            sb.toString().trim()
        } catch (e: Exception) { null }
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

    private fun flattenCoordinates(arr: JSONArray, sb: StringBuilder) {
        if (arr.length() == 0) return
        val first = arr.get(0)
        if (first is Number) {
            val lng = arr.getDouble(0)
            val lat = arr.getDouble(1)
            sb.append("$lng,$lat ")
        } else if (first is JSONArray) {
            for (i in 0 until arr.length()) {
                flattenCoordinates(arr.getJSONArray(i), sb)
            }
        }
    }

    private fun isPointInGeoJsonGeometry(lat: Double, lng: Double, geometry: JSONObject): Boolean {
        return try {
            val type = geometry.optString("type")
            val coordinates = geometry.getJSONArray("coordinates")
            
            if (type.equals("Polygon", ignoreCase = true)) {
                // Polygon: coordinates[0] is outer ring
                if (coordinates.length() > 0) {
                    val ring = parseRing(coordinates.getJSONArray(0))
                    return isPointInPolygon(lat, lng, ring)
                }
            } else if (type.equals("MultiPolygon", ignoreCase = true)) {
                // MultiPolygon: coordinates[i][0] are outer rings
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
            Log.d(TAG, "HTTP RESP Code: $code")
            
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                // Loguear solo si no es excesivamente largo
                if (response.length < 2000) Log.d(TAG, "HTTP RESP Body: $response")
                else Log.d(TAG, "HTTP RESP Body (Truncated): ${response.substring(0, 2000)}...")
                response
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "HTTP Error Code: $code. Error Body: $errorStream")
                null
            }
        } catch (e: Exception) { 
            Log.e(TAG, "HTTP Exception: ${e.message}")
            e.printStackTrace()
            null 
        }
    }
}
