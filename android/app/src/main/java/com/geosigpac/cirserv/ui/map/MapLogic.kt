
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

    /**
     * Actualiza la información y el resaltado (Solo Recintos).
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

            // Ajuste de sensibilidad
            val sensitivity = if (currentZoom < 14.5) 10f else 5f
            
            val searchArea = RectF(
                screenPoint.x - sensitivity, screenPoint.y - sensitivity, 
                screenPoint.x + sensitivity, screenPoint.y + sensitivity
            )

            // Consultamos solo capa de recintos para el resaltado
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            
            if (features.isNotEmpty()) {
                val feature = features.first() // Tomamos el primero
                val currentRef = extractSigpacRef(feature)

                if (currentRef != lastSelectedRef) {
                    applyHighlight(map, feature)
                    lastSelectedRef = currentRef
                }
                return currentRef
            }
            
            if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
            return ""
            
        } catch (e: Exception) {
            Log.e("MapLogic", "Error updateRealtimeInfo: ${e.message}")
            return ""
        }
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

    private fun applyHighlight(map: MapLibreMap, feature: Feature?) {
        val style = map.style ?: return

        // 1. FILTRO RECINTO (Único activo)
        val conditions = mutableListOf<Expression>()
        if (feature != null) {
            SIGPAC_KEYS.forEach { key ->
                if (feature.hasProperty(key)) {
                    val prop = feature.getProperty(key).asJsonPrimitive
                    val value: Any = when {
                        prop.isNumber -> prop.asNumber
                        prop.isBoolean -> prop.asBoolean
                        else -> prop.asString
                    }
                    conditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                }
            }
            if (conditions.isNotEmpty()) {
                val filter = Expression.all(*conditions.toTypedArray())
                (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(filter)
            }
        } else {
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
        }

        // Aseguramos que el resaltado de cultivo esté apagado
        (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(Expression.literal(false))
    }

    private fun clearHighlight(map: MapLibreMap) {
        val emptyFilter = Expression.literal(false)
        map.style?.let { style ->
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
            (style.getLayer(LAYER_CULTIVO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
        }
        lastSelectedRef = ""
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

        // Mantenemos la carga de datos de cultivo solo para la ficha informativa, aunque no se resalte
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
