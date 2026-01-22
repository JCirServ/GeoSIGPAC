
package com.geosigpac.cirserv.services

import android.util.Log
import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.SigpacData
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL

object SigpacApiService {

    private const val TAG = "SigpacApiService"

    suspend fun fetchHydration(referencia: String): Pair<SigpacData?, CultivoData?> = withContext(Dispatchers.IO) {
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

        val sigpac = fetchUrl(recintoUrl)?.let { jsonStr ->
            try {
                val array = JSONArray(jsonStr)
                if (array.length() > 0) {
                    val props = array.getJSONObject(0)
                    
                    // Traducir código de uso
                    val rawUso = props.optString("uso_sigpac")
                    val translatedUso = SigpacCodeManager.getUsoDescription(rawUso)

                    // Traducir código de región
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
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "GeoSIGPAC-Mobile/1.0")
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }
}
