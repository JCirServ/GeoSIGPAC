
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
const val LAYER_RECINTO_FILL = "recinto-layer-fill" // Capa invisible para detección
const val LAYER_RECINTO_HIGHLIGHT_FILL = "recinto-layer-highlight-fill" // Capa iluminada relleno
const val LAYER_RECINTO_HIGHLIGHT_LINE = "recinto-layer-highlight-line" // Capa iluminada borde
const val SOURCE_LAYER_ID_RECINTO = "recinto"

const val SOURCE_CULTIVO = "cultivo-source"
const val LAYER_CULTIVO_FILL = "cultivo-layer-fill"
const val SOURCE_LAYER_ID_CULTIVO = "cultivo_declarado"

// NUEVO: Capa de Resultados de Búsqueda (GeoJSON directo)
const val SOURCE_SEARCH_RESULT = "search-result-source"
const val LAYER_SEARCH_RESULT_FILL = "search-result-fill"
const val LAYER_SEARCH_RESULT_LINE = "search-result-line"

// Campos para identificar unívocamente un recinto
val SIGPAC_KEYS = listOf("provincia", "municipio", "agregado", "zona", "poligono", "parcela", "recinto")

// --- COORDENADAS POR DEFECTO (Comunidad Valenciana) ---
const val VALENCIA_LAT = 39.4699
const val VALENCIA_LNG = -0.3763
const val DEFAULT_ZOOM = 16.0
const val USER_TRACKING_ZOOM = 16.0

// --- COLORES TEMA CAMPO ---
val FieldBackground = Color(0xFF121212)
val FieldSurface = Color(0xFF252525)
val FieldGreen = Color(0xFF00FF88) // Updated to Neon Green
val HighContrastWhite = Color(0xFFFFFFFF)
val FieldGray = Color(0xFFB0B0B0)
val FieldDivider = Color(0xFF424242)

// Colores de capas de mapa
val HighlightColor = Color(0xFFF97316) // Naranja SIGPAC para selección

// COLORES DINÁMICOS SEGÚN MAPA BASE
val RecintoColorPNOA = Color(0xFF00E5FF) // Cian Neón (Alto contraste sobre satélite oscuro)
val RecintoColorOSM = Color(0xFF6200EA)  // Púrpura Intenso (Alto contraste sobre mapa claro)

enum class BaseMap(val title: String) {
    OSM("OpenStreetMap"),
    PNOA("Ortofoto PNOA")
}

data class ParcelSearchResult(
    val bounds: LatLngBounds,
    val feature: Feature
)

// --- FUNCIONES API (WFS & INFO) ---

/**
 * Calcula los límites y la geometría de una parcela basada en su data local KML
 * sin necesidad de llamadas a API externa.
 */
fun computeLocalBoundsAndFeature(parcela: NativeParcela): ParcelSearchResult? {
    if (parcela.geometryRaw.isNullOrEmpty()) return null

    return try {
        // Caso A: GeoJSON (Recinto hidratado desde API)
        if (parcela.geometryRaw.trim().startsWith("{")) {
            // Workaround: Envolvemos la geometría cruda en un objeto Feature para poder usar Feature.fromJson
            // ya que Geometry.fromJson a veces da problemas de resolución en tiempo de compilación/linkado.
            val rawGeom = parcela.geometryRaw.trim()
            val featureJson = """{"type": "Feature", "properties": {}, "geometry": $rawGeom}"""
            val feature = Feature.fromJson(featureJson)

            // Calculamos bounds simples iterando coordenadas del JSON raw (más rápido que parsear el objeto Feature completo manualmente)
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

            if (minLat == Double.MAX_VALUE) return null

            val latPadding = (maxLat - minLat) * 0.20
            val lngPadding = (maxLng - minLng) * 0.20
            
            val bounds = LatLngBounds.from(maxLat + latPadding, maxLng + lngPadding, minLat - latPadding, minLng - lngPadding)
            return ParcelSearchResult(bounds, feature)
        }

        // Caso B: KML String legacy (Pares lat,lng separados por espacios)
        val points = mutableListOf<Point>()
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE

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

        if (points.isEmpty()) return null

        val latPadding = (maxLat - minLat) * 0.20
        val lngPadding = (maxLng - minLng) * 0.20
        
        val bounds = LatLngBounds.from(
            maxLat + latPadding,
            maxLng + lngPadding,
            minLat - latPadding,
            minLng - lngPadding
        )

        if (points.first() != points.last()) {
            points.add(points.first())
        }
        val polygon = Polygon.fromLngLats(listOf(points))
        val feature = Feature.fromGeometry(polygon)
        
        ParcelSearchResult(bounds, feature)
    } catch (e: Exception) {
        Log.e("MapUtils", "Error parsing local geometry: ${e.message}")
        null
    }
}

/**
 * Busca la ubicación de un recinto o parcela utilizando el endpoint GeoJSON de recinfoparc.
 * Devuelve tanto el BoundingBox para la cámara como el Feature para dibujarlo inmediatamente.
 */
suspend fun searchParcelLocation(prov: String, mun: String, pol: String, parc: String, rec: String?): ParcelSearchResult? = withContext(Dispatchers.IO) {
    try {
        // Valores por defecto para agregado y zona en búsquedas rápidas
        val ag = "0"
        val zo = "0"

        // URL proporcionada por el usuario para localización de parcelas/recintos
        val urlString = String.format(
            Locale.US,
            "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc/%s/%s/%s/%s/%s/%s.geojson",
            prov, mun, ag, zo, pol, parc
        )

        Log.d("SEARCH_SIGPAC", "Requesting: $urlString")

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")

        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            connection.disconnect()

            val responseString = response.toString()
            val featureCollection = JSONObject(responseString)
            val features = featureCollection.optJSONArray("features")

            if (features != null && features.length() > 0) {
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLng = Double.MAX_VALUE
                var maxLng = -Double.MAX_VALUE
                var foundFeature: JSONObject? = null

                for (i in 0 until features.length()) {
                    val featureObj = features.getJSONObject(i)
                    val props = featureObj.optJSONObject("properties")
                    
                    // Si se especificó un recinto, buscamos exactamente ese. Si no, tomamos el primero o unimos (aquí simplificado al primero válido)
                    if (rec != null && props != null) {
                        val currentRec = props.optString("recinto")
                        if (currentRec != rec) continue
                    }

                    // Encontramos el candidato
                    foundFeature = featureObj
                    
                    val geometry = featureObj.optJSONObject("geometry") ?: continue
                    val coordinates = geometry.optJSONArray("coordinates") ?: continue

                    // Calcular Bounds manualmente para asegurar encuadre perfecto
                    fun extractPoints(arr: JSONArray) {
                        if (arr.length() == 0) return
                        val first = arr.get(0)
                        if (first is Double || first is Int) {
                            val lng = arr.getDouble(0)
                            val lat = arr.getDouble(1)
                            if (lat < minLat) minLat = lat
                            if (lat > maxLat) maxLat = lat
                            if (lng < minLng) minLng = lng
                            if (lng > maxLng) maxLng = lng
                        } else if (first is JSONArray) {
                            for (j in 0 until arr.length()) {
                                extractPoints(arr.getJSONArray(j))
                            }
                        }
                    }

                    extractPoints(coordinates)
                    // Si buscamos un recinto específico, paramos al encontrarlo
                    if (rec != null) break
                }

                if (foundFeature != null && minLat != Double.MAX_VALUE) {
                    // Crear Feature de MapLibre desde el JSON
                    val mapLibreFeature = Feature.fromJson(foundFeature.toString())

                    // Margen de seguridad del 30% para que se vea contexto
                    val latPadding = (maxLat - minLat) * 0.30
                    val lngPadding = (maxLng - minLng) * 0.30
                    
                    val bounds = LatLngBounds.from(
                        maxLat + latPadding, 
                        maxLng + lngPadding, 
                        minLat - latPadding, 
                        minLng - lngPadding
                    )
                    
                    return@withContext ParcelSearchResult(bounds, mapLibreFeature)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("SEARCH_SIGPAC", "Error: ${e.message}")
    }
    return@withContext null
}

suspend fun fetchFullSigpacInfo(lat: Double, lng: Double): Map<String, String>? = withContext(Dispatchers.IO) {
    try {
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000; connection.readTimeout = 5000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
        
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

                // Traducir Uso
                val rawUso = getProp("uso_sigpac")
                val translatedUso = SigpacCodeManager.getUsoDescription(rawUso) ?: rawUso

                // Traducir Región
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
