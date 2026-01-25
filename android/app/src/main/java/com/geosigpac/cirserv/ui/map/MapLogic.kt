
package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
import android.util.Log
import com.geosigpac.cirserv.ui.*
import com.geosigpac.cirserv.utils.SigpacCodeManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature

object MapLogic {

    private var lastSelectedRef = "" 
    private var lastSelectedCultivoHash = ""

    // Claves específicas para identificar inequívocamente un cultivo en el mapa vectorial
    private val CULTIVO_IDENTIFIER_KEYS = listOf(
        "exp_num",       // Expediente (String)
        "parc_producto", // Código Cultivo (Number)
        "parc_supcult",  // Superficie Cultivo (Number) - Diferencia subdivisiones
        "parc_sistexp",  // Sistema Explotación (String)
        "tipo_aprovecha" // Aprovechamiento (String)
    )

    /**
     * Actualiza la información y el resaltado.
     * Referencia simplificada: Prov:Mun:Pol:Par:Rec
     */
    fun updateRealtimeInfo(map: MapLibreMap): String {
        val currentZoom = map.cameraPosition.zoom
        
        if (currentZoom < 13.5) {
            if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
            return ""
        }

        try {
            val center = map.cameraPosition.target ?: return ""
            val screenPoint = map.projection.toScreenLocation(center)

            // Ajuste de sensibilidad dinámica según zoom
            val sensitivity = if (currentZoom < 14.5) 10f else 5f
            
            val searchArea = RectF(
                screenPoint.x - sensitivity, screenPoint.y - sensitivity, 
                screenPoint.x + sensitivity, screenPoint.y + sensitivity
            )

            // Consultamos capas
            val features = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL, LAYER_RECINTO_FILL)
            
            if (features.isNotEmpty()) {
                // --- DEBUG LOGGING: RAW DATA CULTIVOS ---
                features.forEachIndexed { i, f ->
                    // Filtramos solo las geometrías que tienen propiedad de producto (Cultivos)
                    if (f.hasProperty("parc_producto")) {
                        Log.d("DEBUG_CULTIVOS", ">>> RAW CULTIVO FOUND [$i] <<<")
                        Log.d("DEBUG_CULTIVOS", "ID: ${f.id()}")
                        Log.d("DEBUG_CULTIVOS", "RAW JSON: ${f.properties()}")
                    }
                }
                // ------------------------------------------------

                // Distinguir entre Recinto y Cultivo basándonos en propiedades
                var cultivoFeature: Feature? = null
                var recintoFeature: Feature? = null

                for (f in features) {
                    if (f.hasProperty("parc_producto") || f.hasProperty("parc_sistexp")) {
                        if (cultivoFeature == null) cultivoFeature = f
                    } else if (f.hasProperty("recinto")) {
                        if (recintoFeature == null) recintoFeature = f
                    }
                    if (cultivoFeature != null && recintoFeature != null) break
                }

                // Fallback: Si el cultivo tiene datos de recinto (que siempre los tiene), úsalo como recinto
                if (recintoFeature == null && cultivoFeature != null) {
                    recintoFeature = cultivoFeature
                }

                if (recintoFeature != null) {
                    val currentRef = extractSigpacRef(recintoFeature)
                    val currentCultivoHash = if (cultivoFeature != null) generateStableCultivoHash(cultivoFeature) else "none"

                    if (currentRef != lastSelectedRef || currentCultivoHash != lastSelectedCultivoHash) {
                        applyHighlight(map, recintoFeature, cultivoFeature)
                        lastSelectedRef = currentRef
                        lastSelectedCultivoHash = currentCultivoHash
                    }
                    return currentRef
                }
            }
            
            if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
            return ""
            
        } catch (e: Exception) {
            Log.e("MapLogic", "Error updateRealtimeInfo: ${e.message}")
            return ""
        }
    }

    private fun generateStableCultivoHash(feature: Feature): String {
        // Usamos solo las claves estables para detectar cambios, ignorando IDs nulos
        val sb = StringBuilder()
        // Siempre incluir referencia geográfica base
        sb.append(extractSigpacRef(feature)).append("|")
        
        // Incluir atributos específicos
        CULTIVO_IDENTIFIER_KEYS.forEach { key ->
            if (feature.hasProperty(key)) {
                sb.append(key).append("=").append(feature.getProperty(key).toString()).append("|")
            }
        }
        return sb.toString()
    }

    private fun extractSigpacRef(feature: Feature): String {
        fun getSafe(key: String): String {
            if (!feature.hasProperty(key)) return ""
            val p = feature.getProperty(key).asJsonPrimitive
            return if (p.isNumber) p.asNumber.toLong().toString() else p.asString.replace("\"", "")
        }

        val prov = getSafe("provincia")
        val mun = getSafe("municipio")
        val pol = getSafe("poligono")
        val parc = getSafe("parcela")
        val rec = getSafe("recinto")

        if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return ""
        return "$prov:$mun:$pol:$parc:$rec"
    }

    private fun applyHighlight(map: MapLibreMap, recintoFeature: Feature?, cultivoFeature: Feature?) {
        val style = map.style ?: return

        // 1. FILTRO BASE (RECINTO)
        val recintoConditions = mutableListOf<Expression>()
        if (recintoFeature != null) {
            SIGPAC_KEYS.forEach { key ->
                if (recintoFeature.hasProperty(key)) {
                    val prop = recintoFeature.getProperty(key).asJsonPrimitive
                    val value: Any = when {
                        prop.isNumber -> prop.asNumber // MapLibre maneja Number genérico (Double/Long/Int)
                        prop.isBoolean -> prop.asBoolean
                        else -> prop.asString
                    }
                    recintoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                }
            }
            if (recintoConditions.isNotEmpty()) {
                val recintoFilter = Expression.all(*recintoConditions.toTypedArray())
                (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(recintoFilter)
            }
        } else {
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
        }

        // 2. FILTRO ESPECÍFICO (CULTIVO)
        if (cultivoFeature != null) {
            val cultivoConditions = mutableListOf<Expression>()
            val fId = cultivoFeature.id()
            
            // A) INTENTO POR ID (Solo si no es nulo ni vacío)
            var useIdStrategy = false
            if (fId != null) {
                // Convertir a String para chequear vacíos
                val idStr = fId.toString()
                if (idStr.isNotEmpty()) {
                    val idAsLong = idStr.toLongOrNull()
                    if (idAsLong != null) {
                        cultivoConditions.add(Expression.eq(Expression.id(), Expression.literal(idAsLong)))
                        useIdStrategy = true
                    } else {
                        cultivoConditions.add(Expression.eq(Expression.id(), Expression.literal(idStr)))
                        useIdStrategy = true
                    }
                }
            }

            // B) ESTRATEGIA PROPIEDADES (Fallback robusto o por defecto si ID falla)
            if (!useIdStrategy) {
                // 1. Añadimos condiciones geo base (obligatorias)
                // Usamos las del propio feature de cultivo para asegurar consistencia
                SIGPAC_KEYS.forEach { key ->
                    if (cultivoFeature.hasProperty(key)) {
                        val prop = cultivoFeature.getProperty(key).asJsonPrimitive
                        val value: Any = when {
                            prop.isNumber -> prop.asNumber
                            prop.isBoolean -> prop.asBoolean
                            else -> prop.asString
                        }
                        cultivoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                    }
                }
                
                // 2. Añadimos SOLO las claves identificadoras de cultivo (Lista Blanca)
                // Esto evita que propiedades "basura" o inestables rompan la igualdad
                CULTIVO_IDENTIFIER_KEYS.forEach { key ->
                    if (cultivoFeature.hasProperty(key)) {
                        val prop = cultivoFeature.getProperty(key).asJsonPrimitive
                        val value: Any = when {
                            prop.isNumber -> prop.asNumber
                            prop.isBoolean -> prop.asBoolean
                            else -> prop.asString
                        }
                        cultivoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                    }
                }
            }
            
            if (cultivoConditions.isNotEmpty()) {
                val cultivoFilter = Expression.all(*cultivoConditions.toTypedArray())
                (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(cultivoFilter)
            } else {
                (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
            }
            
        } else {
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
        }
    }

    private fun clearHighlight(map: MapLibreMap) {
        val emptyFilter = Expression.literal(false)
        map.style?.let { style ->
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
        }
        lastSelectedRef = ""
        lastSelectedCultivoHash = ""
    }

    suspend fun fetchExtendedData(map: MapLibreMap): Pair<Map<String, String>?, Map<String, String>?> {
        if (map.cameraPosition.zoom < 13.5) return Pair(null, null)
        val center = map.cameraPosition.target ?: return Pair(null, null)
        
        val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
        
        val recintoData = fullData ?: run {
            val screenPoint = map.projection.toScreenLocation(center)
            val features = map.queryRenderedFeatures(RectF(screenPoint.x-5f, screenPoint.y-5f, screenPoint.x+5f, screenPoint.y+5f), LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                mutableMapOf<String, String>().apply {
                    features[0].properties()?.entrySet()?.forEach { 
                        this[it.key] = it.value.toString().replace("\"", "") 
                    }
                }
            } else null
        }

        val cultivoData = queryLayerData(map, center, LAYER_CULTIVO_FILL)
        return Pair(recintoData, cultivoData)
    }

    private fun queryLayerData(map: MapLibreMap, point: LatLng, layerId: String): Map<String, String>? {
        val screenPoint = map.projection.toScreenLocation(point)
        val features = map.queryRenderedFeatures(RectF(screenPoint.x-10f, screenPoint.y-10f, screenPoint.x+10f, screenPoint.y+10f), layerId)
        if (features.isEmpty()) return null
        
        val props = features[0].properties() ?: return null
        return mutableMapOf<String, String>().apply {
            props.entrySet().forEach { entry ->
                val key = entry.key
                var value = entry.value.toString().replace("\"", "")
                
                value = when(key) {
                    "tipo_aprovecha" -> SigpacCodeManager.getAprovechamientoDescription(value) ?: value
                    "parc_producto", "cultsecun_producto" -> SigpacCodeManager.getProductoDescription(value) ?: value
                    else -> value
                }
                this[key] = value
            }
        }
    }
}
