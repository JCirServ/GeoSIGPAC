package com.geosigpac.cirserv.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
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
val FieldGreen = Color(0xFF66BB6A)
val HighContrastWhite = Color(0xFFFFFFFF)
val FieldGray = Color(0xFFB0B0B0)
val FieldDivider = Color(0xFF424242)
val HighlightColor = Color(0xFFF97316) // Naranja SIGPAC

enum class BaseMap(val title: String) {
    OSM("OpenStreetMap"),
    PNOA("Ortofoto PNOA")
}

// --- FUNCIONES API (WFS & INFO) ---

suspend fun searchParcelLocation(prov: String, mun: String, pol: String, parc: String, rec: String?): LatLngBounds? = withContext(Dispatchers.IO) {
    try {
        val ag = "0"
        val zo = "0"
        val targetRec = rec ?: "1"

        val urlString = String.format(
            "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recincentroid/%s/%s/%s/%s/%s/%s/%s.json",
            prov, mun, ag, zo, pol, parc, targetRec
        )

        Log.d("SEARCH_API", "Requesting: $urlString")

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
            val json = JSONObject(responseString)
            
            var coords: JSONArray? = null
            if (json.has("coordinates")) {
                coords = json.getJSONArray("coordinates")
            } else if (json.has("geometry")) {
                val geom = json.getJSONObject("geometry")
                if (geom.has("coordinates")) {
                    coords = geom.getJSONArray("coordinates")
                }
            }

            if (coords != null && coords.length() >= 2) {
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                val offset = 0.0025 
                return@withContext LatLngBounds.from(lat + offset, lon + offset, lat - offset, lon - offset)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("SEARCH_API", "Exception: ${e.message}")
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

                return@withContext mapOf(
                    "provincia" to prov, "municipio" to mun, "agregado" to getProp("agregado"),
                    "zona" to getProp("zona"), "poligono" to pol, "parcela" to getProp("parcela"), "recinto" to getProp("recinto"),
                    "superficie" to superficieRaw, "pendiente_media" to getProp("pendiente_media"), "altitud" to altitudVal,
                    "uso_sigpac" to getProp("uso_sigpac"), "subvencionabilidad" to getProp("coef_admisibilidad_pastos"),
                    "coef_regadio" to getProp("coef_regadio"), "incidencias" to getProp("incidencias").replace("[", "").replace("]", "").replace("\"", ""),
                    "region" to getProp("region")
                )
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return@withContext null
}
