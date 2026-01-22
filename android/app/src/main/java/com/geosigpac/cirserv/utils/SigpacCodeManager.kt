
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

    // APROVECHAMIENTO
    private const val FILE_APROVECHAMIENTO = "cod_aprovechamiento.json"
    private const val URL_APROVECHAMIENTO = "https://sigpac-hubcloud.es/codigossigpac/cod_aprovechamiento.json"

    // LINEAS DE AYUDA (Solicitadas)
    private const val FILE_LINEASAD = "cod_lineasad.json"
    private const val URL_LINEASAD = "https://sigpac-hubcloud.es/codigossigpac/cod_lineasad.json"

    // LINEAS DE AYUDA PDR
    private const val FILE_LINEASAD_PDR = "cod_lineasad_pdr.json"
    private const val URL_LINEASAD_PDR = "https://sigpac-hubcloud.es/codigossigpac/cod_lineasad_pdr.json"

    // PRODUCTOS (CULTIVOS)
    private const val FILE_PRODUCTO = "cod_producto.json"
    private const val URL_PRODUCTO = "https://sigpac-hubcloud.es/codigossigpac/cod_producto.json"

    // Mapas en memoria: Código -> Descripción
    private var usoMap: MutableMap<String, String> = mutableMapOf()
    private var regionMap: MutableMap<String, String> = mutableMapOf()
    private var incidenciaMap: MutableMap<String, String> = mutableMapOf()
    private var aprovechamientoMap: MutableMap<String, String> = mutableMapOf()
    private var lineasadMap: MutableMap<String, String> = mutableMapOf()
    private var lineasadPdrMap: MutableMap<String, String> = mutableMapOf()
    private var productoMap: MutableMap<String, String> = mutableMapOf()
    
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

            // --- APROVECHAMIENTO ---
            val fileAprovecha = File(context.filesDir, FILE_APROVECHAMIENTO)
            if (!fileAprovecha.exists()) downloadFile(URL_APROVECHAMIENTO, fileAprovecha)
            if (fileAprovecha.exists()) {
                try {
                    parseAprovechamientoJson(fileAprovecha.readText())
                    Log.d(TAG, "Códigos de aprovechamiento cargados: ${aprovechamientoMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing aprovechamiento JSON: ${e.message}")
                    fileAprovecha.delete()
                }
            }

            // --- LINEAS DE AYUDA ---
            val fileLineas = File(context.filesDir, FILE_LINEASAD)
            if (!fileLineas.exists()) downloadFile(URL_LINEASAD, fileLineas)
            if (fileLineas.exists()) {
                try {
                    parseLineasAdJson(fileLineas.readText())
                    Log.d(TAG, "Códigos de líneas ayuda cargados: ${lineasadMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing lineas ayuda JSON: ${e.message}")
                    fileLineas.delete()
                }
            }

            // --- LINEAS DE AYUDA PDR ---
            val fileLineasPdr = File(context.filesDir, FILE_LINEASAD_PDR)
            if (!fileLineasPdr.exists()) downloadFile(URL_LINEASAD_PDR, fileLineasPdr)
            if (fileLineasPdr.exists()) {
                try {
                    parseLineasAdPdrJson(fileLineasPdr.readText())
                    Log.d(TAG, "Códigos de líneas ayuda PDR cargados: ${lineasadPdrMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing lineas ayuda PDR JSON: ${e.message}")
                    fileLineasPdr.delete()
                }
            }

            // --- PRODUCTOS ---
            val fileProducto = File(context.filesDir, FILE_PRODUCTO)
            if (!fileProducto.exists()) downloadFile(URL_PRODUCTO, fileProducto)
            if (fileProducto.exists()) {
                try {
                    parseProductosJson(fileProducto.readText())
                    Log.d(TAG, "Códigos de producto cargados: ${productoMap.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing productos JSON: ${e.message}")
                    fileProducto.delete()
                }
            }
            
            // --- INYECCIÓN MANUAL DE CÓDIGOS ---
            injectManualCodes()

            isInitialized = true
        }
    }

    private fun injectManualCodes() {
        val manualCodes = mapOf(
            "17651010502" to "Apicultura para la biodiversidad. Convocatoria 2025",
            "17651011401" to "Mantenimiento o mejora de hábitats y de actividades agrarias tradicionales que preserven la biodiversidad (cultivo de arroz). Convocatoria 2023",
            "17651010701" to "Protección de la avifauna (aves esteparias). Convocatoria 2023",
            "17653000101" to "Compromisos de gestión medioambiental en agricultura ecológica. Convocatoria 2023",
            "17655010201" to "Compromisos de conservación de recursos genéticos: conservación oveja guirra. Convocatoria 2023",
            "17013010005" to "Ayudas compensatorias a zonas de montaña. Convocatoria 2025",
            "17013020003" to "Ayuda a zonas distintas de las de montaña con limitaciones naturales. Convocatoria 2025"
        )
        
        manualCodes.forEach { (code, desc) ->
            if (!lineasadMap.containsKey(code)) {
                lineasadMap[code] = desc
            }
            if (!lineasadPdrMap.containsKey(code)) {
                lineasadPdrMap[code] = desc
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
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) incidenciaMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseAprovechamientoJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) aprovechamientoMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseLineasAdJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) lineasadMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseLineasAdPdrJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) lineasadPdrMap[codigo] = descripcion
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun parseProductosJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val codigos = root.optJSONArray("codigos") ?: return
            
            for (i in 0 until codigos.length()) {
                val item = codigos.getJSONObject(i)
                val codigo = item.optString("codigo") 
                val descripcion = item.optString("descripcion")
                if (codigo.isNotEmpty()) productoMap[codigo] = descripcion
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
     * Devuelve "Código - Descripción" del producto.
     */
    fun getProductoDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        val desc = productoMap[code] ?: productoMap[code.toIntOrNull()?.toString()]
        return if (desc != null) "$code - $desc" else code
    }

    /**
     * Devuelve la descripción de la región (ej: "1-TCS")
     */
    fun getRegionDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        return regionMap[code] ?: code
    }

    /**
     * Devuelve SOLO la descripción del aprovechamiento.
     */
    fun getAprovechamientoDescription(code: String?): String? {
        if (code.isNullOrEmpty()) return null
        val desc = aprovechamientoMap[code] ?: aprovechamientoMap[code.toIntOrNull()?.toString()]
        return desc ?: code
    }

    /**
     * Formatea incidencias.
     */
    fun getFormattedIncidencias(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val cleaned = raw.replace("[", "").replace("]", "").replace("\"", "")
        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return parts.map { code ->
            val desc = incidenciaMap[code] ?: incidenciaMap[code.toIntOrNull()?.toString()]
            if (desc != null) "$code - $desc" else code
        }
    }

    /**
     * Formatea ayudas solicitadas.
     */
    fun getFormattedAyudas(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val cleaned = raw.replace("[", "").replace("]", "").replace("\"", "")
        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return parts.map { code ->
            val desc = lineasadMap[code] ?: lineasadMap[code.toIntOrNull()?.toString()]
            if (desc != null) "$code - $desc" else code
        }
    }

    /**
     * Formatea ayudas PDR.
     */
    fun getFormattedAyudasPdr(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val cleaned = raw.replace("[", "").replace("]", "").replace("\"", "")
        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return parts.map { code ->
            val desc = lineasadPdrMap[code] ?: lineasadPdrMap[code.toIntOrNull()?.toString()]
            if (desc != null) "$code - $desc" else code
        }
    }
}
