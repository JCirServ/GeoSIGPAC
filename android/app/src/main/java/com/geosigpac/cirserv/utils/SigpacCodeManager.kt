
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
    
    // USOS
    private const val FILE_USOS = "cod_uso_sigpac.json"
    private const val URL_USOS = "https://sigpac-hubcloud.es/codigossigpac/cod_uso_sigpac.json"
    
    // REGIONES
    private const val FILE_REGIONES = "cod_region_2023.json"
    private const val URL_REGIONES = "https://sigpac-hubcloud.es/codigossigpac/cod_region_2023.json"

    // INCIDENCIAS
    private const val FILE_INCIDENCIAS = "cod_incidencia.json"
    private const val URL_INCIDENCIAS = "https://sigpac-hubcloud.es/codigossigpac/cod_incidencia.json"

    // Mapas en memoria: Código -> Descripción
    private var usoMap: MutableMap<String, String> = mutableMapOf()
    private var regionMap: MutableMap<String, String> = mutableMapOf()
    private var incidenciaMap: MutableMap<String, String> = mutableMapOf()
    
    private var isInitialized = false

    suspend fun initialize(context: Context) {
        if (isInitialized) return
        withContext(Dispatchers.IO) {
            // --- USOS ---
            val fileUsos = File(context.filesDir, FILE_USOS)
            if (!fileUsos.exists()) downloadFile(URL_USOS, fileUsos)
            if (fileUsos.exists()) {
                try {
                    parseUsosJson(fileUsos.readText())
                    Log.d(TAG, "Códigos de uso cargados: ${usoMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing usos JSON: ${e.message}")
                    fileUsos.delete()
                }
            }

            // --- REGIONES ---
            val fileRegiones = File(context.filesDir, FILE_REGIONES)
            if (!fileRegiones.exists()) downloadFile(URL_REGIONES, fileRegiones)
            if (fileRegiones.exists()) {
                try {
                    parseRegionesJson(fileRegiones.readText())
                    Log.d(TAG, "Códigos de región cargados: ${regionMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing regiones JSON: ${e.message}")
                    fileRegiones.delete()
                }
            }

            // --- INCIDENCIAS ---
            val fileIncidencias = File(context.filesDir, FILE_INCIDENCIAS)
            if (!fileIncidencias.exists()) downloadFile(URL_INCIDENCIAS, fileIncidencias)
            if (fileIncidencias.exists()) {
                try {
                    parseIncidenciasJson(fileIncidencias.readText())
                    Log.d(TAG, "Códigos de incidencia cargados: ${incidenciaMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing incidencias JSON: ${e.message}")
                    fileIncidencias.delete()
                }
            }

            isInitialized = true
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
                if (codigo.isNotEmpty()) usoMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseRegionesJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) regionMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseIncidenciasJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                // En incidencias también suele ser numérico
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) incidenciaMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * Devuelve el código formateado: "CA (VIALES)"
     */
    fun getUsoDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        val desc = usoMap[code] ?: usoMap[code.uppercase()]
        return if (desc != null) "$code ($desc)" else code
    }

    /**
     * Devuelve la descripción de la región (ej: "1-TCS")
     */
    fun getRegionDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        return regionMap[code] ?: code
    }

    /**
     * Toma un string raw (ej: "7, 11") y devuelve una lista de strings formateados:
     * ["7 - Uso asignado por...", "11 - Árboles dispersos"]
     */
    fun getFormattedIncidencias(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        
        // Limpiar brackets y comillas por si acaso vienen del JSON raw
        val cleaned = raw.replace("[", "").replace("]", "").replace("\"", "")
        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        return parts.map { code ->
            val desc = incidenciaMap[code] ?: incidenciaMap[code.toIntOrNull()?.toString()]
            if (desc != null) {
                "$code - $desc"
            } else {
                code
            }
        }
    }
}
