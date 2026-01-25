
package com.geosigpac.cirserv.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.geojson.Geometry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// --- CONSTANTES DE CAPAS ---
const val SOURCE_BASE = "base-source"
const val LAYER_BASE = "base-layer"

// Capas SIGPAC (MVT)
const val SOURCE_RECINTO = "recinto-source"
const val LAYER_RECINTO_LINE = "recinto-layer-line"
const val LAYER_RECINTO_FILL = "recinto-layer-fill"
const val LAYER_RECINTO_HIGHLIGHT_FILL = "recinto-layer-highlight-fill"
const val LAYER_RECINTO_HIGHLIGHT_LINE = "recinto-layer-highlight-line"
const val SOURCE_LAYER_ID_RECINTO = "recinto"

const val SOURCE_CULTIVO = "cultivo-source"
const val LAYER_CULTIVO_FILL = "cultivo-layer-fill"
const val LAYER_CULTIVO_HIGHLIGHT_FILL = "cultivo-layer-highlight-fill"
const val LAYER_CULTIVO_HIGHLIGHT_LINE = "cultivo-layer-highlight-line"
const val SOURCE_LAYER_ID_CULTIVO = "cultivo_declarado"

// Capa de Resultados de Búsqueda
const val SOURCE_SEARCH_RESULT = "search-result-source"
const val LAYER_SEARCH_RESULT_FILL = "search-result-fill"
const val LAYER_SEARCH_RESULT_LINE = "search-result-line"

// Claves básicas para identificar un RECINTO único
val SIGPAC_KEYS = listOf("provincia", "municipio", "agregado", "zona", "poligono", "parcela", "recinto")

// Claves extendidas para diferenciar CULTIVOS dentro de un mismo recinto
val CULTIVO_KEYS = listOf("parc_producto", "parc_sistexp", "tipo_aprovecha", "parc_supcult")

// --- COORDENADAS POR DEFECTO ---
const val VALENCIA_LAT = 39.4699
const val VALENCIA_LNG = -0.3763
const val DEFAULT_ZOOM = 16.0
const val USER_TRACKING_ZOOM = 16.0

// --- COLORES TEMA ---
val FieldBackground = Color(0xFF121212)
val FieldSurface = Color(0xFF252525)
val FieldGreen = Color(0xFF00FF88)
val HighContrastWhite = Color(0xFFFFFFFF)
val FieldGray = Color(0xFFB0B0B0)
val FieldDivider = Color(0xFF424242)

// --- NUEVA PALETA DE COLORES MAPA (Alto Contraste) ---

// 1. RECINTOS (Estructural - Cian/Blanco Frío)
// PNOA: Casi transparente, solo borde blanco fuerte.
val BorderColorPNOA = Color(0xFFFFFFFF) 
val FillColorPNOA   = Color(0x15E0F7FA) // Cian muy tenue (Alpha bajo)

// OSM: Gris oscuro.
val BorderColorOSM  = Color(0xFF263238)
val FillColorOSM    = Color(0x2037474F)

// 2. CULTIVOS (Agronómico - Amarillo Oro)
val CultivoFillColor = Color(0xFFFFD600) // Amarillo Oro
val CultivoFillOpacity = 0.25f // Opacidad media

// 3. SELECCIÓN / RETÍCULA (Interacción Actualizada)
// Recinto: Naranja Claro (Fondo general del recinto)
val HighlightColorRecinto = Color(0xFFFFAB40) // Orange Accent 200 / Naranja Claro Vibrante

// Cultivo: Naranja Oscuro (Selección específica de la subdivisión)
val HighlightColorCultivo = Color(0xFFD84315) // Deep Orange 800 / Naranja Quemado
val HighlightOpacity = 0.50f

// 4. KML / PROYECTOS (Usuario - Violeta/Índigo)
// Definido en MapLayers.kt

enum class BaseMap(val title: String) {
    OSM("OpenStreetMap"),
    PNOA("Ortofoto PNOA")
}

data class ParcelSearchResult(
    val bounds: LatLngBounds,
    val feature: Feature
)

// --- FUNCIONES API (WFS & INFO) ---
// (Sin cambios en las funciones de lógica de negocio)

fun computeLocalBoundsAndFeature(parcela: NativeParcela): ParcelSearchResult? {
    if (!parcela.geometryRaw.isNullOrEmpty()) {
        try {
            if (parcela.geometryRaw.trim().startsWith("{")) {
                val rawGeom = parcela.geometryRaw.trim()
                val featureJson = """{"type": "Feature", "properties": {}, "geometry": $rawGeom}"""
                val feature = Feature.fromJson(featureJson)
                val root = JSONObject(parcela.geometryRaw)
                val coords = root.optJSONArray("coordinates")
                var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
                fun traverse(arr: JSONArray) {
                    if (arr.length() == 0) return
                    val first = arr.get(0)
                    if (first is Number) {
                        val lng = arr.getDouble(0)
                        val lat = arr.getDouble(1)
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                        if (lng < minLng) minLng = lng
                        if (lng > maxLng) maxLng = lng
                    } else if (first is JSONArray) {
                        for (i in 0 until arr.length()) traverse(arr.getJSONArray(i))
                    }
                }
                if (coords != null) traverse(coords)
                if (minLat != Double.MAX_VALUE) {
                    val latPadding = (maxLat - minLat) * 0.20
                    val lngPadding = (maxLng - minLng) * 0.20
                    val bounds = LatLngBounds.from(maxLat + latPadding, maxLng + lngPadding, minLat - latPadding, minLng - lngPadding)
                    return ParcelSearchResult(bounds, feature)
                }
            }
            val points = mutableListOf<Point>()
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
            val coordPairs = parcela.geometryRaw.trim().split("\\s+".toRegex())
            coordPairs.forEach { pair ->
                val coords = pair.split(",")
                if (coords.size >= 2) {
                    val lng = coords[0].toDoubleOrNull()
                    val lat = coords[1].toDoubleOrNull()
                    if (lng != null && lat != null) {
                        points.add(Point.fromLngLat(lng, lat))
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                        if (lng < minLng) minLng = lng
                        if (lng > maxLng) maxLng = lng
                    }
                }
            }
            if (points.isNotEmpty()) {
                val latPadding = (maxLat - minLat) * 0.20
                val lngPadding = (maxLng - minLng) * 0.20
                val bounds = LatLngBounds.from(maxLat + latPadding, maxLng + lngPadding, minLat - latPadding, minLng - lngPadding)
                if (points.first() != points.last()) { points.add(points.first()) }
                val polygon = Polygon.fromLngLats(listOf(points))
                val feature = Feature.fromGeometry(polygon)
                return ParcelSearchResult(bounds, feature)
            }
        } catch (e: Exception) {
            Log.e("MapUtils", "Error parsing local geometry: ${e.message}")
        }
    }
    if (parcela.centroidLat != null && parcela.centroidLng != null) {
        val lat = parcela.centroidLat
        val lng = parcela.centroidLng
        val pointFeature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
        val delta = 0.002
        val bounds = LatLngBounds.from(lat + delta, lng + delta, lat - delta, lng - delta)
        return ParcelSearchResult(bounds, pointFeature)
    }
    return null
}

suspend fun searchParcelLocation(prov: String, mun: String, pol: String, parc: String, rec: String?): ParcelSearchResult? = withContext(Dispatchers.IO) {
    try {
        val ag = "0"; val zo = "0"
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/%s/%s/%s/%s/%s/%s.geojson", prov, mun, ag, zo, pol, parc)
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.requestMethod = "GET"; connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder(); var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close(); connection.disconnect()
            val responseString = response.toString()
            val featureCollection = JSONObject(responseString)
            val features = featureCollection.optJSONArray("features")
            if (features != null && features.length() > 0) {
                var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
                var foundFeature: JSONObject? = null
                for (i in 0 until features.length()) {
                    val featureObj = features.getJSONObject(i)
                    val props = featureObj.optJSONObject("properties")
                    if (rec != null && props != null) {
                        val currentRec = props.optString("recinto")
                        if (currentRec != rec) continue
                    }
                    foundFeature = featureObj
                    val geometry = featureObj.optJSONObject("geometry") ?: continue
                    val coordinates = geometry.optJSONArray("coordinates") ?: continue
                    fun extractPoints(arr: JSONArray) {
                        if (arr.length() == 0) return
                        val first = arr.get(0)
                        if (first is Double || first is Int) {
                            val lng = arr.getDouble(0); val lat = arr.getDouble(1)
                            if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat
                            if (lng < minLng) minLng = lng; if (lng > maxLng) maxLng = lng
                        } else if (first is JSONArray) {
                            for (j in 0 until arr.length()) extractPoints(arr.getJSONArray(j))
                        }
                    }
                    extractPoints(coordinates)
                    if (rec != null) break
                }
                if (foundFeature != null && minLat != Double.MAX_VALUE) {
                    val mapLibreFeature = Feature.fromJson(foundFeature.toString())
                    val latPadding = (maxLat - minLat) * 0.30
                    val lngPadding = (maxLng - minLng) * 0.30
                    val bounds = LatLngBounds.from(maxLat + latPadding, maxLng + lngPadding, minLat - latPadding, minLng - lngPadding)
                    return@withContext ParcelSearchResult(bounds, mapLibreFeature)
                }
            }
        }
    } catch (e: Exception) { Log.e("SEARCH_SIGPAC", "Error: ${e.message}") }
    return@withContext null
}

suspend fun fetchFullSigpacInfo(lat: Double, lng: Double): Map<String, String>? = withContext(Dispatchers.IO) {
    try {
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000
        connection.requestMethod = "GET"; connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder(); var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close(); connection.disconnect()
            val jsonResponse = response.toString().trim()
            var targetJson: JSONObject? = null
            if (jsonResponse.startsWith("[")) {
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) targetJson = jsonArray.getJSONObject(0)
            } else if (jsonResponse.startsWith("{")) targetJson = JSONObject(jsonResponse)
            if (targetJson != null) {
                fun getProp(key: String): String {
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    val props = targetJson!!.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optString(key)
                    val features = targetJson!!.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        val fProps = features.getJSONObject(0).optJSONObject("properties")
                        if (fProps != null && fProps.has(key)) return fProps.optString(key)
                    }
                    return ""
                }
                val prov = getProp("provincia"); val mun = getProp("municipio"); val pol = getProp("poligono")
                if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return@withContext null
                val superficieRaw = getProp("superficie")
                var altitudVal = getProp("altitud"); if (altitudVal.isEmpty()) { altitudVal = getProp("altitud_media") }
                val rawUso = getProp("uso_sigpac")
                val translatedUso = SigpacCodeManager.getUsoDescription(rawUso) ?: rawUso
                val rawRegion = getProp("region")
                val translatedRegion = SigpacCodeManager.getRegionDescription(rawRegion) ?: rawRegion
                return@withContext mapOf(
                    "provincia" to prov, "municipio" to mun, "agregado" to getProp("agregado"),
                    "zona" to getProp("zona"), "poligono" to pol, "parcela" to getProp("parcela"), "recinto" to getProp("recinto"),
                    "superficie" to superficieRaw, "pendiente_media" to getProp("pendiente_media"), "altitud" to altitudVal,
                    "uso_sigpac" to translatedUso, "subvencionabilidad" to getProp("coef_admisibilidad_pastos"),
                    "coef_regadio" to getProp("coef_regadio"), "incidencias" to getProp("incidencias").replace("[", "").replace("]", "").replace("\"", ""),
                    "region" to translatedRegion
                )
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return@withContext null
}
