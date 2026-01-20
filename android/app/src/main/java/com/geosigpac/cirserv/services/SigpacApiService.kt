
package com.geosigpac.cirserv.services

import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.SigpacData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL

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
                        provincia = props.optInt("provincia"),
                        municipio = props.optInt("municipio"),
                        agregado = props.optInt("agregado"),
                        zona = props.optInt("zona"),
                        poligono = props.optInt("poligono"),
                        parcela = props.optInt("parcela"),
                        recinto = props.optInt("recinto"),
                        pendiente = props.optDouble("pendiente_media"),
                        altitud = props.optDouble("altitud")
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
                        producto = props.optInt("parc_producto"),
                        sistExp = props.optString("parc_sistexp"),
                        supCult = props.optDouble("parc_supcult"),
                        ayudaSol = props.optString("parc_ayudasol"),
                        pdrRec = props.optString("pdr_rec"),
                        indCultApro = props.optInt("parc_indcultapro"),
                        tipoAprovecha = props.optString("tipo_aprovecha")
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
