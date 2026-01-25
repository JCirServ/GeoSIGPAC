
package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
import android.util.Log
import com.geosigpac.cirserv.ui.*
import com.geosigpac.cirserv.utils.SigpacCodeManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.geometry.LatLng

object MapLogic {

    private var lastSelectedRef = "" 
    private var lastSelectedCultivoHash = ""

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

            // 1. Consultamos AMBAS capas para asegurar que pillamos algo si el usuario apunta
            // Priorizamos Cultivo porque suele ser la geometría más específica (interna)
            val features = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL, LAYER_RECINTO_FILL)
            
            if (features.isNotEmpty()) {
                // --- DEBUG LOGGING: INSPECCIÓN DE PROPIEDADES ---
                Log.d("DEBUG_CULTIVOS", "--- DETECCIÓN BAJO PUNTERO (Total: ${features.size}) ---")
                features.take(5).forEachIndexed { i, f ->
                    val tipo = if (f.hasProperty("parc_producto")) "CULTIVO" else "RECINTO/BASE"
                    // Imprimimos ID y todas las propiedades para encontrar la clave única
                    Log.d("DEBUG_CULTIVOS", "[$i] TIPO: $tipo | ID: ${f.id()} | PROPS: ${f.properties()}")
                }
                // ------------------------------------------------

                // Tomamos la primera característica
                val feature = features[0]
                val currentRef = extractSigpacRef(feature)
                
                // Generar un hash robusto usando TODAS las claves de diferenciación de cultivo
                // Antes solo usaba parc_producto, lo que fallaba si había dos cultivos del mismo tipo con diferente superficie/riego.
                val currentCultivoHash = generateCultivoHash(feature)

                // Solo refrescamos el filtro si ha cambiado la parcela/recinto o el cultivo interno
                if (currentRef != lastSelectedRef || currentCultivoHash != lastSelectedCultivoHash) {
                    applyHighlight(map, feature)
                    lastSelectedRef = currentRef
                    lastSelectedCultivoHash = currentCultivoHash
                }
                
                return currentRef
            } else {
                if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
                return ""
            }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun generateCultivoHash(feature: org.maplibre.geojson.Feature): String {
        // Si no es un cultivo (es recinto base), devolvemos hash estático
        if (!feature.hasProperty("parc_producto")) return "recinto_base"

        // Concatenamos valores de claves clave para detectar cualquier cambio de geometría
        val sb = StringBuilder()
        CULTIVO_KEYS.forEach { key ->
            if (feature.hasProperty(key)) {
                sb.append(feature.getProperty(key).toString()).append("|")
            } else {
                sb.append("null|")
            }
        }
        return sb.toString()
    }

    /**
     * Extrae solo los 5 códigos esenciales para formar la referencia única.
     */
    private fun extractSigpacRef(feature: org.maplibre.geojson.Feature): String {
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

        // Formato estándar: 28:079:1:25:1
        return "$prov:$mun:$pol:$parc:$rec"
    }

    private fun applyHighlight(map: MapLibreMap, feature: org.maplibre.geojson.Feature) {
        val style = map.style ?: return

        // 1. FILTRO BASE (RECINTO) - Se aplica a la capa de resaltado Naranja (Fondo)
        // Utiliza solo las claves geográficas del Recinto (Prov, Mun, Pol, Parc, Rec)
        val recintoConditions = mutableListOf<Expression>()
        SIGPAC_KEYS.forEach { key ->
            if (feature.hasProperty(key)) {
                val prop = feature.getProperty(key).asJsonPrimitive
                val value: Any = when {
                    prop.isNumber -> prop.asNumber
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

        // 2. FILTRO ESPECÍFICO (CULTIVO) - Se aplica a la capa de resaltado Cian (Superior)
        // Si el feature clickado tiene propiedades de cultivo (producto, etc.), usamos un filtro MÁS estricto.
        // Si es solo un recinto base (sin datos de cultivo), limpiamos esta capa superior.
        
        val isCultivoFeature = feature.hasProperty("parc_producto") || feature.hasProperty("parc_sistexp")
        
        if (isCultivoFeature) {
            val cultivoConditions = mutableListOf<Expression>()
            
            // Añadimos las condiciones base del recinto (obligatorias para localizar la zona)
            cultivoConditions.addAll(recintoConditions)
            
            // Añadimos las condiciones específicas del cultivo (obligatorias para distinguir la subdivisión)
            CULTIVO_KEYS.forEach { key ->
                if (feature.hasProperty(key)) {
                    val prop = feature.getProperty(key).asJsonPrimitive
                    val value: Any = when {
                        prop.isNumber -> prop.asNumber
                        prop.isBoolean -> prop.asBoolean
                        else -> prop.asString
                    }
                    cultivoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                }
            }
            
            val cultivoFilter = Expression.all(*cultivoConditions.toTypedArray())
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(cultivoFilter)
            
        } else {
            // Si estamos sobre un recinto genérico o fuera de un cultivo definido, ocultamos el highlight específico
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
        }
    }

    private fun clearHighlight(map: MapLibreMap) {
        val emptyFilter = Expression.literal(false)
        map.style?.let { style ->
            // Limpiar Recintos - SOLO FILL
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
            
            // Limpiar Cultivos - SOLO FILL
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
        }
        lastSelectedRef = ""
        lastSelectedCultivoHash = ""
    }

    /**
     * Fetch de datos extendidos (API -> Fallback PBF)
     */
    suspend fun fetchExtendedData(map: MapLibreMap): Pair<Map<String, String>?, Map<String, String>?> {
        if (map.cameraPosition.zoom < 13.5) return Pair(null, null)
        val center = map.cameraPosition.target ?: return Pair(null, null)
        
        // 1. API externa (Ya resuelto por el usuario)
        val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
        
        // 2. Fallback PBF si la API no devuelve nada
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

        // 3. Capa de Cultivo Declarado
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
