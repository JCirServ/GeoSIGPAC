
package com.geosigpac.cirserv.services

import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.SigpacData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object SigpacApiService {

    suspend fun fetchHydration(referencia: String): Pair<SigpacData?, CultivoData?> = withContext(Dispatchers.IO) {
        val parts = referencia.split(":", "-")
        if (parts.size < 5) return@withContext Pair(null, null)

        val prov = parts[0]
        val mun = parts[1]
        val pol = parts[parts.size - 3]
        val parc = parts[parts.size - 2]
        val rec = parts[parts.size - 1]

        val baseUrl = "https://sigpac-hubcloud.es/ogcapi/collections"
        val query = "provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"

        val sigpac = fetchUrl("$baseUrl/recintos/items?$query")?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    val props = features.getJSONObject(0).getJSONObject("properties")
                    SigpacData(
                        pendiente = props.optDouble("pendiente_media"),
                        altitud = props.optDouble("altitud"),
                        municipio = props.optString("municipio"),
                        poligono = props.optString("poligono"),
                        parcela = props.optString("parcela"),
                        recinto = props.optString("recinto"),
                        provincia = props.optString("provincia")
                    )
                } else null
            } catch (e: Exception) { null }
        }

        val cultivo = fetchUrl("$baseUrl/cultivo_declarado/items?$query")?.let { jsonStr ->
            try {
                val root = JSONNative(jsonStr)
                val features = root.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    val props = features.getJSONObject(0).getJSONObject("properties")
                    CultivoData(
                        expNum = props.optString("exp_num"),
                        producto = props.optString("parc_producto"),
                        sistExp = if (props.optString("parc_sistexp") == "R") "Regadío" else "Secano",
                        ayudaSol = props.optString("parc_ayudasol"),
                        superficie = props.optDouble("parc_supcult") / 10000.0
                    )
                } else null
            } catch (e: Exception) { null }
        }

        Pair(sigpac, cultivo)
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }
}
