
package com.geosigpac.cirserv.services

import android.util.Log
import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.SigpacData
import com.geosigpac.cirserv.utils.NetworkResult
import com.geosigpac.cirserv.utils.NetworkUtils
import com.geosigpac.cirserv.utils.RetryPolicy
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONObject as JSONNative
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

object SigpacApiService {

    private const val TAG = "SigpacApiService"
    
    // Cache simple en memoria: URL -> Pair(Timestamp, ResponseString)
    private val memoryCache = ConcurrentHashMap<String, Pair<Long, String>>()
    private const val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutos

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

        val recintoUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfo/$prov/$mun/$ag/$zo/$pol/$parc/$rec.json"
        val ogcQuery = "provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        val cultivoUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?$ogcQuery"

        // Ejecutar llamadas en paralelo sería ideal, pero por simplicidad y robustez secuencial:
        val sigpac = fetchUrlWithRetry(recintoUrl)?.let { parseSigpacDataJson(it) }

        val cultivo = fetchUrlWithRetry(cultivoUrl)?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                
                if (features != null && features.length() > 0) {
                    val candidates = mutableListOf<JSONObject>()
                    for (i in 0 until features.length()) {
                        candidates.add(features.getJSONObject(i))
                    }

                    // LÓGICA DE SELECCIÓN DE REGISTRO
                    val bestFeature = candidates.sortedWith(Comparator { o1, o2 ->
                        val p1 = o1.optJSONObject("properties")
                        val p2 = o2.optJSONObject("properties")
                        if (p1 == null || p2 == null) return@Comparator 0

                        if (pointCheck != null) {
                            val geom1 = o1.optJSONObject("geometry")
                            val geom2 = o2.optJSONObject("geometry")
                            val inside1 = if(geom1 != null) isPointInGeoJsonGeometry(pointCheck.first, pointCheck.second, geom1) else false
                            val inside2 = if(geom2 != null) isPointInGeoJsonGeometry(pointCheck.first, pointCheck.second, geom2) else false
                            
                            if (inside1 && !inside2) return@Comparator -1
                            if (!inside1 && inside2) return@Comparator 1
                        }

                        if (targetAreaHa != null && targetAreaHa > 0) {
                            val targetRounded = (targetAreaHa * 100.0).roundToInt() / 100.0
                            val area1Ha = p1.optDouble("parc_supcult", 0.0) / 10000.0
                            val area2Ha = p2.optDouble("parc_supcult", 0.0) / 10000.0
                            val diff1 = abs(area1Ha - targetRounded)
                            val diff2 = abs(area2Ha - targetRounded)
                            if (abs(diff1 - diff2) > 0.001) return@Comparator diff1.compareTo(diff2)
                        }

                        val y1 = p1.optInt("exp_ano", 0)
                        val y2 = p2.optInt("exp_ano", 0)
                        if (y1 != y2) return@Comparator y2.compareTo(y1)

                        fun parseExp(s: String?): Long = s?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
                        val e1 = parseExp(p1.optString("exp_num", "0"))
                        val e2 = parseExp(p2.optString("exp_num", "0"))
                        return@Comparator e2.compareTo(e1)
                    }).firstOrNull()

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

        Triple(sigpac, cultivo, null)
    }

    suspend fun recoverParcelaFromPoint(lat: Double, lng: Double): Triple<String?, String?, SigpacData?> = withContext(Dispatchers.IO) {
        val identifyUrl = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val idResponse = fetchUrlWithRetry(identifyUrl) ?: return@withContext Triple(null, null, null)

        var prov = ""; var mun = ""; var pol = ""; var parc = ""; var rec = ""; var agg = "0"; var zon = "0"
        var sigpacData: SigpacData? = null
        var geometryRaw: String? = null

        try {
            val jsonArray = JSONArray(idResponse)
            if (jsonArray.length() > 0) {
                val item = jsonArray.getJSONObject(0)
                
                fun getProp(key: String): String {
                    if (item.has(key)) return item.getString(key)
                    val props = item.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.getString(key)
                    return ""
                }
                
                fun getDoubleProp(key: String): Double {
                    if (item.has(key)) return item.optDouble(key)
                    val props = item.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optDouble(key)
                    return 0.0
                }
                
                fun getIntProp(key: String): Int {
                    if (item.has(key)) return item.optInt(key)
                    val props = item.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optInt(key)
                    return 0
                }

                prov = getProp("provincia"); mun = getProp("municipio")
                agg = getProp("agregado"); if(agg.isEmpty()) agg = "0"
                zon = getProp("zona"); if(zon.isEmpty()) zon = "0"
                pol = getProp("poligono"); parc = getProp("parcela"); rec = getProp("recinto")

                val translatedUso = SigpacCodeManager.getUsoDescription(getProp("uso_sigpac"))
                val translatedRegion = SigpacCodeManager.getRegionDescription(getProp("region"))
                
                sigpacData = SigpacData(
                    superficie = getDoubleProp("superficie"),
                    pendienteMedia = getDoubleProp("pendiente_media"),
                    coefRegadio = getDoubleProp("coef_regadio"),
                    admisibilidad = getDoubleProp("coef_admisibilidad_pastos"),
                    usoSigpac = translatedUso,
                    region = translatedRegion,
                    altitud = getIntProp("altitud")
                )

                val wkt = getProp("wkt")
                if (wkt.isNotEmpty()) {
                    geometryRaw = wktToGeoJson(wkt)
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Error parsing recinfobypoint: ${e.message}")
            return@withContext Triple(null, null, null) 
        }

        if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return@withContext Triple(null, null, null)

        val fullRef = "$prov:$mun:$pol:$parc:$rec"

        if (geometryRaw != null) {
            return@withContext Triple(fullRef, geometryRaw, sigpacData)
        }

        // Fallback: recinfoparc
        val recUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/$prov/$mun/$agg/$zon/$pol/$parc/$rec.geojson"
        val recJson = fetchUrlWithRetry(recUrl)
        if (recJson != null) {
            geometryRaw = extractGeometryFromGeoJson(recJson)
        }

        // Fallback: cultivo declarado
        if (geometryRaw == null) {
            val ogcUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
            val cultivoJson = fetchUrlWithRetry(ogcUrl)
            if (cultivoJson != null) {
                try {
                    val root = JSONNative(cultivoJson)
                    val features = root.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        geometryRaw = extractGeometryFromGeoJsonObj(features.getJSONObject(0).optJSONObject("geometry"))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        return@withContext Triple(fullRef, geometryRaw, sigpacData)
    }

    /**
     * Envoltura con RetryPolicy y Cache.
     */
    private suspend fun fetchUrlWithRetry(urlString: String): String? {
        // 1. Revisar Caché
        memoryCache[urlString]?.let { (timestamp, data) ->
            if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
                Log.v(TAG, "Cache HIT: $urlString")
                return data
            } else {
                memoryCache.remove(urlString)
            }
        }

        // 2. Ejecutar Red con Retry
        return try {
            val result = RetryPolicy.executeWithRetry {
                NetworkUtils.fetchUrl(urlString)
            }

            if (result is NetworkResult.Success) {
                memoryCache[urlString] = System.currentTimeMillis() to result.data
                result.data
            } else {
                Log.w(TAG, "Fallo red (agotados reintentos) para: $urlString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción crítica en fetch: ${e.message}")
            null
        }
    }

    // --- Métodos Privados de Parseo (Sin cambios lógicos mayores) ---

    private fun wktToGeoJson(wkt: String): String? {
        try {
            if (!wkt.startsWith("POLYGON")) return null
            val innerContent = wkt.substringAfter("POLYGON").trim()
                .removePrefix("(").removeSuffix(")")
                .removePrefix("(").removeSuffix(")") 
            
            val points = innerContent.split(",")
            val jsonCoords = StringBuilder("[[")
            
            points.forEachIndexed { index, pointRaw ->
                val coords = pointRaw.trim().split("\\s+".toRegex())
                if (coords.size >= 2) {
                    if (index > 0) jsonCoords.append(",")
                    jsonCoords.append("[${coords[0]},${coords[1]}]")
                }
            }
            jsonCoords.append("]]")
            return """{"type": "Polygon", "coordinates": $jsonCoords}"""
        } catch (e: Exception) { return null }
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
        } catch (e: Exception) { }
        return null
    }

    private fun extractGeometryFromGeoJsonObj(geometry: JSONObject?): String? = geometry?.toString()

    private fun extractGeometryFromGeoJson(jsonStr: String): String? {
        return try {
            val root = JSONNative(jsonStr)
            var geometry: JSONNative? = null
            if (root.has("features")) {
                val features = root.getJSONArray("features")
                if (features.length() > 0) geometry = features.getJSONObject(0).optJSONObject("geometry")
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
                if (coordinates.length() > 0) isPointInPolygon(lat, lng, parseRing(coordinates.getJSONArray(0))) else false
            } else if (type.equals("MultiPolygon", ignoreCase = true)) {
                for (i in 0 until coordinates.length()) {
                    if (isPointInPolygon(lat, lng, parseRing(coordinates.getJSONArray(i).getJSONArray(0)))) return true
                }
                false
            } else false
        } catch (e: Exception) { false }
    }

    private fun parseRing(jsonRing: JSONArray): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until jsonRing.length()) {
            val pt = jsonRing.getJSONArray(i)
            list.add(pt.getDouble(1) to pt.getDouble(0))
        }
        return list
    }

    private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]
            if (((latI > lat) != (latJ > lat)) && (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)) inside = !inside
            j = i
        }
        return inside
    }
}
