
package com.geosigpac.cirserv.ui.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object CameraSigpacHelper {
    
    suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.requestMethod = "GET"; connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
            
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) response.append(line)
                reader.close(); connection.disconnect()
                
                val jsonResponse = response.toString().trim()
                var targetJson: JSONObject? = null
                
                if (jsonResponse.startsWith("[")) {
                    val jsonArray = JSONArray(jsonResponse)
                    if (jsonArray.length() > 0) targetJson = jsonArray.getJSONObject(0)
                } else if (jsonResponse.startsWith("{")) targetJson = JSONObject(jsonResponse)

                if (targetJson != null) {
                    fun findKey(key: String): String {
                        if (targetJson!!.has(key)) return targetJson!!.optString(key)
                        val props = targetJson!!.optJSONObject("properties"); if (props != null && props.has(key)) return props.optString(key)
                        val features = targetJson!!.optJSONArray("features"); if (features != null && features.length() > 0) {
                            val firstFeature = features.getJSONObject(0)
                            val featProps = firstFeature.optJSONObject("properties"); if (featProps != null && featProps.has(key)) return featProps.optString(key)
                        }
                        return ""
                    }
                    val prov = findKey("provincia"); val mun = findKey("municipio"); val pol = findKey("poligono")
                    val parc = findKey("parcela"); val rec = findKey("recinto"); val uso = findKey("uso_sigpac")
                    if (prov.isNotEmpty() && mun.isNotEmpty()) return@withContext Pair("$prov:$mun:$pol:$parc:$rec", uso)
                }
            }
        } catch (e: Exception) {}
        return@withContext Pair(null, null)
    }
}
