
package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
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

                // Si hay cultivo pero no recinto detectado explícitamente (ej: cultivo encima tapando),
                // usamos el cultivo como fuente de datos de recinto también.
                if (recintoFeature == null && cultivoFeature != null) {
                    recintoFeature = cultivoFeature
                }

                // Si tenemos al menos un recinto (base), procedemos
                if (recintoFeature != null) {
                    val currentRef = extractSigpacRef(recintoFeature)
                    val currentCultivoHash = if (cultivoFeature != null) generateStrictCultivoHash(cultivoFeature) else "none"

                    if (currentRef != lastSelectedRef || currentCultivoHash != lastSelectedCultivoHash) {
                        applyHighlight(map, recintoFeature, cultivoFeature)
                        lastSelectedRef = currentRef
                        lastSelectedCultivoHash = currentCultivoHash
                    }
                    return currentRef
                }
            }
            
            // Si no hay features válidos
            if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
            return ""
            
        } catch (e: Exception) {
            return ""
        }
    }

    private fun generateStrictCultivoHash(feature: Feature): String {
        if (feature.id() != null) {
            return "ID:${feature.id()}"
        }
        val sb = StringBuilder()
        val props = feature.properties()
        if (props != null) {
            for (entry in props.entrySet()) {
                sb.append(entry.key).append("=").append(entry.value.toString()).append("|")
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

        // 1. FILTRO BASE (RECINTO) - Naranja Claro
        if (recintoFeature != null) {
            val recintoConditions = mutableListOf<Expression>()
            SIGPAC_KEYS.forEach { key ->
                if (recintoFeature.hasProperty(key)) {
                    val prop = recintoFeature.getProperty(key).asJsonPrimitive
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
        } else {
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
        }

        // 2. FILTRO ESPECÍFICO (CULTIVO) - Naranja Oscuro
        if (cultivoFeature != null) {
            val cultivoConditions = mutableListOf<Expression>()
            val fId = cultivoFeature.id()
            
            if (fId != null) {
                // FIX CRÍTICO: Las teselas vectoriales suelen usar IDs numéricos. 
                // Comparar ID numérico con String literal falla en MapLibre native.
                // Intentamos parsear a Long para pasar un literal numérico si es posible.
                val idAsLong = fId.toLongOrNull()
                if (idAsLong != null) {
                    cultivoConditions.add(Expression.eq(Expression.id(), Expression.literal(idAsLong)))
                } else {
                    cultivoConditions.add(Expression.eq(Expression.id(), Expression.literal(fId)))
                }
            } else {
                // Fallback: Comparar todas las propiedades
                // Primero añadimos las geo-claves del recinto para acotar búsqueda
                if (recintoFeature != null) {
                    SIGPAC_KEYS.forEach { key ->
                        if (recintoFeature.hasProperty(key)) {
                            val prop = recintoFeature.getProperty(key).asJsonPrimitive
                            val value: Any = when {
                                prop.isNumber -> prop.asNumber
                                prop.isBoolean -> prop.asBoolean
                                else -> prop.asString
                            }
                            cultivoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                        }
                    }
                }
                
                // Luego añadimos propiedades específicas del cultivo
                val props = cultivoFeature.properties()
                if (props != null) {
                    for (entry in props.entrySet()) {
                        val key = entry.key
                        if (SIGPAC_KEYS.contains(key)) continue // Ya añadidas
                        
                        val element = entry.value
                        if (element.isJsonPrimitive) {
                            val prim = element.asJsonPrimitive
                            val value: Any = when {
                                prim.isNumber -> prim.asNumber
                                prim.isBoolean -> prim.asBoolean
                                else -> prim.asString
                            }
                            cultivoConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                        }
                    }
                }
            }
            
            val cultivoFilter = Expression.all(*cultivoConditions.toTypedArray())
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(cultivoFilter)
            
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
