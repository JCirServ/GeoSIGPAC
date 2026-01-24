
package com.geosigpac.cirserv.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class AnalysisSeverity {
    OK,
    WARNING,
    CRITICAL
}

data class AgroAnalysisResult(
    val severity: AnalysisSeverity,
    val isCompatible: Boolean, // Mantenido por compatibilidad, equivale a severity != CRITICAL
    val explanation: String,
    val requirements: List<FieldGuideEntry>
)

data class FieldGuideEntry(
    val code: String? = null,
    val keywords: List<String>? = null,
    val excludeCodes: List<String>? = null,
    val description: String,
    val requirement: String
)

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

    // --- REGLAS AGRONÓMICAS (FIELD GUIDE) ---
    private val FIELD_GUIDE = listOf(
        FieldGuideEntry(code = "18", description = "[18] Ayuda Básica a la Renta (ABR) / Condicionalidad", requirement = "Actividad Agraria: Comprobar que no hay abandono.\nCondicionalidad: No residuos, Regadío/Secano OK.\nNo labrar a favor de pendiente (si Pendiente>10% sin terraza).\nNo arar en cultivos de invierno (cereales) antes del 1 de septiembre."),
        FieldGuideEntry(code = "5011", description = "[5011] Ecorregimen Pastos Húmedos", requirement = "Pastoreo Extensivo (505): Animales de la explotación (crotal) (>1), evidencias de pastoreo.\nSiega Sostenible (506): No siega en Julio y Agosto. Máx. 3 cortes (≤300m) o 2 cortes (>300m)."),
        FieldGuideEntry(code = "5012", description = "[5012] Ecorregimen Pastos Mediterráneos", requirement = "Pastoreo Extensivo (505): Animales de la explotación (crotal) (>1), evidencias de pastoreo.\nSiega Sostenible (506): No siega en Julio y Agosto. Máx. 3 cortes (≤300m) o 2 cortes (>300m)."),
        FieldGuideEntry(code = "5021", description = "[5021] Ecorregimen T. Cultivo Secano (Rotación/Siembra Directa)", requirement = "Siembra Directa (508): Mantener rastrojo, suelo nunca desnudo.\nRotación (509): Cultivo en campo = Declarado.\nBiodiversidad (512): 7% superficie no productiva."),
        FieldGuideEntry(code = "5022", description = "[5022] Ecorregimen T. Cultivo Secano Húmedo (Rotación/Siembra Directa)", requirement = "Siembra Directa (508): Mantener rastrojo, suelo nunca desnudo.\nRotación (509): Cultivo en campo = Declarado.\nBiodiversidad (512): 7% superficie no productiva."),
        FieldGuideEntry(code = "5023", description = "[5023] Ecorregimen T. Cultivo Regadío (Rotación/Siembra Directa)", requirement = "Siembra Directa (508): Mantener rastrojo, suelo nunca desnudo.\nRotación (509): Cultivo en campo = Declarado.\nBiodiversidad (512): 4% superficie no productiva."),
        FieldGuideEntry(code = "5031", description = "[5031] Ecorregimen Leñosos (Cubiertas Vegetales/Inertes)", requirement = "Cubierta Viva (510): Cubrir 40% ancho libre de copa (mín. 0.5m). No herbicidas/laboreos profundos.\nCubierta Inerte (511): Triturado de poda cubriendo 40% ancho libre de copa."),
        FieldGuideEntry(code = "5032", description = "[5032] Ecorregimen Leñosos (Cubiertas Vegetales/Inertes)", requirement = "Cubierta Viva (510): Cubrir 40% ancho libre de copa (mín. 0.5m). No herbicidas/laboreos profundos.\nCubierta Inerte (511): Triturado de poda cubriendo 40% ancho libre de copa."),
        FieldGuideEntry(code = "5033", description = "[5033] Ecorregimen Leñosos (Cubiertas Vegetales/Inertes)", requirement = "Cubierta Viva (510): Cubrir 40% ancho libre de copa (mín. 0.5m). No herbicidas/laboreos profundos.\nCubierta Inerte (511): Triturado de poda cubriendo 40% ancho libre de copa."),
        FieldGuideEntry(code = "5041", description = "[5041] Ecorregimen Agroecología: Espacios de Biodiversidad", requirement = "Cultivos Permanentes (Leñosos): Elementos paisaje 4% sup. presentada.\nTierras Cultivo (Secano): 7%.\nTierras Cultivo (Regadío): 4%."),
        FieldGuideEntry(code = "513", description = "[513] Ecorregimen Arroz", requirement = "Verificar la práctica declarada:\nNivelación anual.\nSiembra en seco.\nSecas intermitentes o Caballones >80cm."),
        FieldGuideEntry(code = "514", description = "[514] Ecorregimen Arroz", requirement = "Verificar la práctica declarada:\nNivelación anual.\nSiembra en seco.\nSecas intermitentes o Caballones >80cm."),
        FieldGuideEntry(code = "515", description = "[515] Ecorregimen Arroz", requirement = "Verificar la práctica declarada:\nNivelación anual.\nSiembra en seco.\nSecas intermitentes o Caballones >80cm."),
        FieldGuideEntry(code = "516", description = "[516] Ecorregimen Arroz", requirement = "Verificar la práctica declarada:\nNivelación anual.\nSiembra en seco.\nSecas intermitentes o Caballones >80cm."),
        FieldGuideEntry(code = "213", description = "[213] Ayuda Frutos Secos Desertificación", requirement = "Densidad Mínima (Almendro: 80/ha, Algarrobo: 30/ha, Avellano: 150/ha).\nSolo Secano.\nPendiente >10% si está fuera de comarcas áridas.\nRecinto > 0,1 ha, y total solicitada > 0,5 ha."),
        FieldGuideEntry(code = "215", description = "[215] Ayuda Olivar en Desventaja", requirement = "Densidad: 30-100 árboles/ha. (ni más ni menos)\nEdad >10 años.\nPendiente ≥25%.\nSolo Secano.\nRecinto > 0,1 ha, y total solicitada > 0,5 ha."),
        FieldGuideEntry(code = "210", description = "[210] Ayuda a la producción sostenible de Arroz", requirement = "Empleo de semilla certificada.\nSiembra antes del 30 Junio."),
        FieldGuideEntry(code = "11", description = "[11] PDR - Agricultura Ecológica", requirement = "Cultivo productivo (no plantones/mantenimiento).\nVerificar no arranques."),
        FieldGuideEntry(keywords = listOf("Esteparias"), description = "[PDR] Esteparias", requirement = "Recinto en áreas subvencionables.\nRastrojo hasta final de Septiembre.\nNo cortar setos/linderos.\nMantener >4% tierra no productiva."),
        FieldGuideEntry(keywords = listOf("Arroz"), excludeCodes = listOf("210", "513", "514", "515", "516"), description = "[PDR] Arroz", requirement = "Mínimo 100 kg/ha semilla certificada.\nAbonos con inhibidores de N.\nControl mecánico de vegetación.\nMantener 85% sup. productiva por 5 años."),
        FieldGuideEntry(keywords = listOf("Guirra"), description = "[PDR] Oveja Guirra", requirement = "Mínimo 30 animales raza Guirra.\nMantener 85% censo inicial (5 años).\nREGA y registro oficial OK.")
    )

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
     * Devuelve la lista de usos comunes ordenados.
     */
    fun getCommonUsos(): List<Pair<String, String>> {
        val priority = listOf("TA", "TH", "FY", "OV", "VI", "FS", "CF", "CS", "PS", "PR", "PA", "IV", "ZC", "ZU", "EP", "FO")
        return priority.map { code ->
            code to (getUsoDescription(code) ?: code)
        }
    }
    
    /**
     * Devuelve TODOS los usos cargados del JSON, ordenados alfabéticamente por código.
     * Si el JSON no se ha cargado aún, devuelve la lista de usos comunes como fallback.
     */
    fun getAllUsos(): List<Pair<String, String>> {
        if (usoMap.isEmpty()) return getCommonUsos()
        
        return usoMap.entries
            .map { it.key to it.value } // (Codigo, Descripcion)
            .sortedBy { it.first }
    }
    
    /**
     * Devuelve TODOS los productos cargados del JSON, ordenados alfabéticamente por NOMBRE.
     * Convierte la clave String a Int para su uso en el modelo.
     */
    fun getAllProductos(): List<Pair<Int, String>> {
        return productoMap.entries.mapNotNull { entry ->
            val code = entry.key.toIntOrNull()
            if (code != null) code to entry.value else null
        }.sortedBy { it.second } // Orden alfabético para facilitar búsqueda humana
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

    // --- AGRO ANALYSIS LOGIC ---

    fun performAgroAnalysis(
        productoCode: Int?,
        productoDesc: String?,
        sigpacUso: String?,
        ayudasRaw: String?,
        pdrRaw: String?,
        sistExp: String?,
        coefRegadio: Double?,
        pendienteMedia: Double?
    ): AgroAnalysisResult {
        var severity = AnalysisSeverity.OK
        val explanationBuilder = StringBuilder()

        // 1. Compatibilidad Cultivo vs Uso SIGPAC (CRITICAL if false)
        val compatibility = checkCompatibility(productoCode, productoDesc, sigpacUso)
        if (!compatibility.first) {
            severity = AnalysisSeverity.CRITICAL
            explanationBuilder.append(compatibility.second)
        }
        
        // 2. Compatibilidad Riego
        val irrigationCheck = checkIrrigationConsistency(sistExp, coefRegadio)
        if (irrigationCheck.second != null) {
            // Si hay mensaje, determina severidad
            if (irrigationCheck.first) { 
                // Es "compatible" pero con advertencia (Orange)
                if (severity != AnalysisSeverity.CRITICAL) severity = AnalysisSeverity.WARNING
            } else {
                // Incompatible (Red)
                severity = AnalysisSeverity.CRITICAL
            }
            if (explanationBuilder.isNotEmpty()) explanationBuilder.append("\n")
            explanationBuilder.append(irrigationCheck.second)
        }

        // 3. Requisitos (Info only, usually)
        val requirements = getFieldRequirements(ayudasRaw, pdrRaw)
        
        // 4. Pendiente (WARNING)
        val normalizedSigpac = sigpacUso?.trim()?.split(" ")?.get(0)?.uppercase()
        val isPastos = listOf("PS", "PR", "PA").contains(normalizedSigpac)
        
        if (pendienteMedia != null && pendienteMedia > 10.0) {
            // Si es pastos y es compatible, no aplica pendiente (no se labra)
            if (!isPastos || !compatibility.first) {
                if (severity == AnalysisSeverity.OK) severity = AnalysisSeverity.WARNING
                if (explanationBuilder.isNotEmpty()) explanationBuilder.append("\n")
                explanationBuilder.append("AVISO PENDIENTE: Valor elevado (${pendienteMedia}%). Verificar laboreo.")
            }
        }

        return AgroAnalysisResult(
            severity = severity,
            isCompatible = (severity != AnalysisSeverity.CRITICAL),
            explanation = explanationBuilder.toString(),
            requirements = requirements
        )
    }

    private fun checkIrrigationConsistency(sistExp: String?, coefRegadio: Double?): Pair<Boolean, String?> {
        if (sistExp == null || coefRegadio == null) return Pair(true, null)
        
        val sys = sistExp.trim().uppercase()
        // SistExp suele venir como "S" o "R" (o palabras completas)
        val isRegadio = sys == "R" || sys.startsWith("REGAD")
        val isSecano = sys == "S" || sys.startsWith("SECAN")

        if (isRegadio) {
            if (coefRegadio == 0.0) {
                return Pair(false, "ERROR RIEGO: Declarado Regadío en recinto de Secano (Coef 0%).")
            }
            if (coefRegadio < 100.0) {
                 return Pair(true, "AVISO RIEGO: Declarado Regadío con Coeficiente parcial ($coefRegadio%).")
            }
        } else if (isSecano) {
            if (coefRegadio == 100.0) {
                 // Si declaras Secano en una parcela de Regadío 100%, es compatible (simplemente no riegas),
                 // pero el usuario solicita verificar la "correspondencia". Generamos un aviso.
                 return Pair(true, "Nota: Declarado Secano en recinto apto para Regadío (100%).")
            }
        }
        return Pair(true, null)
    }

    private fun checkCompatibility(productoCode: Int?, productoDesc: String?, sigpacUso: String?): Pair<Boolean, String> {
        if (sigpacUso.isNullOrEmpty()) return Pair(false, "Falta uso SIGPAC")
        
        val normalizedSigpac = sigpacUso.trim().split(" ")[0].uppercase() // "TA (TIERRAS...)" -> "TA"
        val prodDisplay = productoDesc ?: "Desconocido"
        
        // Mapeo Heurístico Rápido de Producto -> Uso SIGPAC Esperado
        val expectedUso = inferUsoFromProduct(productoCode, productoDesc) ?: return Pair(true, "") // Producto sin regla definida, asumimos compatible silencioso

        if (expectedUso == normalizedSigpac) {
            return Pair(true, "") // Compatible silencioso
        }

        // Reglas de compatibilidad cruzada
        val groupTaThIv = listOf("TA", "TH", "IV")
        if (groupTaThIv.contains(expectedUso) && groupTaThIv.contains(normalizedSigpac)) {
             return Pair(true, "") // Grupo intercambiable compatible silencioso
        }

        // Pastos (PS, PR, PA) y Pastizales (Code 62)
        if (expectedUso == "PASTOS" && listOf("PS", "PR", "PA").contains(normalizedSigpac)) {
             return Pair(true, "") // Pastos compatibles silencioso
        }
        // Compatibilidad entre tipos de pastos antiguos
        if ((expectedUso == "PS" && normalizedSigpac == "PR") || (expectedUso == "PR" && normalizedSigpac == "PS")) {
             return Pair(true, "") 
        }

        // Leñosos compatibles
        if ((expectedUso == "FY" || expectedUso == "CI") && normalizedSigpac == "CF") return Pair(true, "")
        if (expectedUso == "OV" && (normalizedSigpac == "FL" || normalizedSigpac == "OF")) return Pair(true, "")
        if (expectedUso == "FS" && (normalizedSigpac == "FL" || normalizedSigpac == "FV")) return Pair(true, "")
        if (expectedUso == "VI" && normalizedSigpac == "FV") return Pair(true, "")
        if (expectedUso == "FY" && normalizedSigpac == "OF") return Pair(true, "")

        return Pair(false, "INCOMPATIBLE: $normalizedSigpac vs $prodDisplay")
    }

    private fun inferUsoFromProduct(code: Int?, desc: String?): String? {
        if (code == 62) return "PASTOS"

        if (desc == null) return null
        val d = desc.uppercase(Locale.getDefault())
        
        return when {
            d.contains("TRIGO") || d.contains("CEBADA") || d.contains("MAIZ") || d.contains("AVENA") || d.contains("GIRASOL") || d.contains("GUISANTE") || d.contains("ALFALFA") || d.contains("BARBECHO") -> "TA"
            d.contains("NARANJ") || d.contains("MANDARIN") || d.contains("LIMON") -> "CI"
            d.contains("OLIVAR") || d.contains("ACEITUNA") -> "OV"
            d.contains("ALMENDRO") || d.contains("NOGAL") || d.contains("AVELLANO") || d.contains("ALGARROBO") -> "FS"
            d.contains("VIÑEDO") || d.contains("UVA") -> "VI"
            d.contains("MANZANO") || d.contains("PERAL") || d.contains("MELOCOTON") || d.contains("CEREZO") || d.contains("CIRUELO") -> "FY"
            d.contains("PASTO") || d.contains("PASTIZAL") -> "PASTOS"
            d.contains("ARROZ") -> "TA" 
            else -> null
        }
    }

    private fun getFieldRequirements(ayudasRaw: String?, pdrRaw: String?): List<FieldGuideEntry> {
        val matches = mutableListOf<FieldGuideEntry>()
        val combinedCodes = mutableListOf<String>()

        // Helper para extraer códigos de strings estilo "[1, 5011]" o "1, 5011"
        fun extract(raw: String?) {
            if (raw.isNullOrEmpty()) return
            val cleaned = raw.replace("[", "").replace("]", "").replace("\"", "")
            val parts = cleaned.split(",").map { it.trim() }
            combinedCodes.addAll(parts)
        }
        
        extract(ayudasRaw)
        extract(pdrRaw)

        // Siempre añadir regla ABR si no está implícita (Regla base PAC)
        val abrEntry = FIELD_GUIDE.find { it.code == "18" }
        if (abrEntry != null) matches.add(abrEntry)

        combinedCodes.forEach { codeOrText ->
            // Buscar por Código Exacto
            val byCode = FIELD_GUIDE.find { it.code == codeOrText }
            if (byCode != null && !matches.contains(byCode)) {
                matches.add(byCode)
            } else {
                // Buscar por Keyword (especialmente para PDRs que a veces vienen como texto)
                val desc = (lineasadMap[codeOrText] ?: lineasadPdrMap[codeOrText] ?: codeOrText).uppercase()
                val byKeyword = FIELD_GUIDE.find { entry ->
                    entry.keywords != null && 
                    entry.keywords.any { k -> desc.contains(k.uppercase()) } &&
                    (entry.excludeCodes == null || !entry.excludeCodes.contains(codeOrText))
                }
                if (byKeyword != null && !matches.contains(byKeyword)) {
                    matches.add(byKeyword)
                }
            }
        }

        return matches
    }
}
