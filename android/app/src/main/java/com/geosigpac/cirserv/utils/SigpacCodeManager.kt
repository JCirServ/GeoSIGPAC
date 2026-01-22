
package com.geosigpac.cirserv.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SigpacCodeManager {
    private const val TAG = "SigpacCodeManager"
    private const val FILE_USOS = "cod_uso_sigpac.json"
    private const val URL_USOS = "https://sigpac-hubcloud.es/codigossigpac/cod_uso_sigpac.json"

    // Mapa en memoria: Código -> Descripción
    private var usoMap: MutableMap<String, String> = mutableMapOf()
    private var isInitialized = false

    suspend fun initialize(context: Context) {
        if (isInitialized) return
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE_USOS)
            
            // 1. Si no existe, descargar
            if (!file.exists()) {
                downloadFile(URL_USOS, file)
            }

            // 2. Cargar en memoria
            if (file.exists()) {
                try {
                    val jsonString = file.readText()
                    parseUsosJson(jsonString)
                    isInitialized = true
                    Log.d(TAG, "Códigos de uso cargados: ${usoMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing local JSON: ${e.message}")
                    // Si falla el parseo local, intentar descargar de nuevo para la próxima
                    file.delete()
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, destFile: File) {
        try {
            Log.d(TAG, "Descargando códigos de: $urlStr")
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Descarga completada.")
            } else {
                Log.e(TAG, "Fallo descarga: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción descarga: ${e.message}")
        }
    }

    private fun parseUsosJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo")
                val descripcion = item.optString("descripcion")
                
                if (codigo.isNotEmpty()) {
                    usoMap[codigo] = descripcion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Devuelve el código formateado: "CA (VIALES)"
     * Si no encuentra descripción, devuelve solo el código: "CA"
     */
    fun getUsoDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        
        // Normalizar clave por si acaso
        val desc = usoMap[code] ?: usoMap[code.uppercase()]
        
        return if (desc != null) {
            "$code ($desc)"
        } else {
            code
        }
    }
}
