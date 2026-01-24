package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
import com.geosigpac.cirserv.ui.*
import com.geosigpac.cirserv.utils.SigpacCodeManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.geometry.LatLng

object MapLogic {

    private var lastSelectedRef = "" 

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

            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            
            if (features.isNotEmpty()) {
                val feature = features[0]
                val currentRef = extractSigpacRef(feature)
                
                // Evitar refrescos de estilo innecesarios
                if (currentRef != lastSelectedRef) {
                    applyHighlight(map, feature)
                    lastSelectedRef = currentRef
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

    /**
     * Extrae solo los 5 códigos esenciales.
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

        // Validamos que los datos críticos existan
        if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return ""

        // Formato: 28:079:1:25:1
        return "$prov:$mun:$pol:$parc:$rec"
    }

    private fun applyHighlight(map: MapLibreMap, feature: org.maplibre.geojson.Feature) {
        val filterConditions = mutableListOf<Expression>()
        
        // Usamos la lista de claves (incluyendo zona/agregado si están en el PBF para que el filtro sea exacto)
        SIGPAC_KEYS.forEach { key ->
            if (feature.hasProperty(key)) {
                val prop = feature.getProperty(key).asJsonPrimitive
                val value: Any = when {
                    prop.isNumber -> prop.asNumber
                    prop.isBoolean -> prop.asBoolean
                    else -> prop.asString
                }
                filterConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
            }
        }

        if (filterConditions.isNotEmpty()) {
            val finalFilter = Expression.all(*filterConditions.toTypedArray())
            map.style?.let { style ->
                (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(finalFilter)
                (style.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE) as? LineLayer)?.setFilter(finalFilter)
            }
        }
    }

    private fun clearHighlight(map: MapLibreMap) {
        val emptyFilter = Expression.literal(false)
        map.style?.let { style ->
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL) as? FillLayer)?.setFilter(emptyFilter)
            (style.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE) as? LineLayer)?.setFilter(emptyFilter)
        }
        lastSelectedRef = ""
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
