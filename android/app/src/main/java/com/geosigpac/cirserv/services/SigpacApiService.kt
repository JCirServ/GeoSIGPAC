
package com.geosigpac.cirserv.services

import com.geosigpac.cirserv.model.CultivoData
import com.geosigpac.cirserv.model.SigpacData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject as JSONNative
import java.net.HttpURLConnection
import java.net.URL

object SigpacApiService {

    suspend fun fetchHydration(referencia: String): Pair<SigpacData?, CultivoData?> = withContext(Dispatchers.IO) {
        val parts = referencia.split(":", "-")
        // Formato esperado completo: Prov:Mun:Ag:Zo:Pol:Parc:Rec (7 partes)
        // Si vienen menos, intentamos mapear las posiciones estándar de atrás hacia adelante
        val prov = parts.getOrNull(0) ?: ""
        val mun = parts.getOrNull(1) ?: ""
        val ag = if (parts.size >= 7) parts[2] else "0"
        val zo = if (parts.size >= 7) parts[3] else "0"
        val pol = parts[parts.size - 3]
        val parc = parts[parts.size - 2]
        val rec = parts[parts.size - 1]

        // NUEVO ENDPOINT DE DETALLE COMPLETO
        val recintoUrl = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfo/$prov/$mun/$ag/$zo/$pol/$parc/$rec.json"
        
        // ENDPOINT OGC PARA CULTIVO (Mantenemos este para la declaración)
        val ogcQuery = "provincia=$prov&municipio=$mun&poligono=$pol&parcela=$parc&recinto=$rec&f=json"
        val cultivoUrl = "https://sigpac-hubcloud.es/ogcapi/collections/cultivo_declarado/items?$ogcQuery"

        val sigpac = fetchUrl(recintoUrl)?.let { jsonStr ->
            try {
                val array = JSONArray(jsonStr)
                if (array.length() > 0) {
                    val props = array.getJSONObject(0)
                    SigpacData(
                        provincia = props.optInt("provincia"),
                        municipio = props.optInt("municipio"),
                        agregado = props.optInt("agregado"),
                        zona = props.optInt("zona"),
                        poligono = props.optInt("poligono"),
                        parcela = props.optInt("parcela"),
                        recinto = props.optInt("recinto"),
                        superficie = props.optDouble("superficie"),
                        pendiente = props.optDouble("pendiente_media"),
                        coefRegadio = if (props.isNull("coef_regadio")) null else props.optDouble("coef_regadio"),
                        admisibilidad = if (props.isNull("admisibilidad")) null else props.optDouble("admisibilidad"),
                        incidencias = props.optString("incidencias")?.replace("[", "")?.replace("]", "")?.replace("\"", ""),
                        uso = props.optString("uso_sigpac"),
                        region = props.optString("region"),
                        altitud = props.optInt("altitud"),
                        srid = props.optInt("srid")
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
            conn.setRequestProperty("User-Agent", "GeoSIGPAC-Mobile/1.0")
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }
}
