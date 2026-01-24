package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
import android.util.Log
import com.geosigpac.cirserv.ui.*
import com.geosigpac.cirserv.utils.SigpacCodeManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.geometry.LatLng

object MapLogic {

    private const val TAG_MAP = "MapLogic"
    private var lastSelectedRef = "" // Caché para evitar cálculos redundantes

    /**
     * Actualiza la información y el resaltado en tiempo real.
     * Optimizada para teselas PBF entre niveles 12 y 15.
     */
    fun updateRealtimeInfo(map: MapLibreMap): String {
        val currentZoom = map.cameraPosition.zoom
        
        // El nivel 13.5 es el umbral ideal donde los recintos PBF suelen ser consultables
        if (currentZoom < 13.5) {
            if (lastSelectedRef.isNotEmpty()) clearHighlight(map)
            return ""
        }

        try {
            val center = map.cameraPosition.target ?: return ""
            val screenPoint = map.projection.toScreenLocation(center)

            // AJUSTE 1: Sensibilidad dinámica según el zoom para evitar fallos de puntería
            val sensitivity = when {
                currentZoom < 14.0 -> 12f // Más margen en zooms lejanos
                currentZoom < 15.5 -> 8f
                else -> 4f // Puntería fina en zoom alto (overzooming)
            }
            
            val searchArea = RectF(
                screenPoint.x - sensitivity, screenPoint.y - sensitivity, 
                screenPoint.x + sensitivity, screenPoint.y + sensitivity
            )

            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            
            if (features.isNotEmpty()) {
                val feature = features[0]
                
                // AJUSTE 2: Extracción y comparación con caché
                val currentRef = extractSigpacRef(feature)
                
                if (currentRef != lastSelectedRef) {
                    applyHighlight(map, feature)
                    lastSelectedRef = currentRef
                }
                
                return currentRef
            } else {
                if (lastSelectedRef.isNotEmpty()) {
                    clearHighlight(map)
                    lastSelectedRef = ""
                }
                return ""
            }
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Aplica el filtro de resaltado asegurando compatibilidad de tipos PBF (Long/Double/String)
     */
    private fun applyHighlight(map: MapLibreMap, feature: org.maplibre.geojson.Feature) {
        val filterConditions = mutableListOf<Expression>()
        
        SIGPAC_KEYS.forEach { key ->
            if (feature.hasProperty(key)) {
                val prop = feature.getProperty(key)
                if (prop.isJsonPrimitive) {
                    val prim = prop.asJsonPrimitive
                    // AJUSTE 3: El PBF puede devolver números como Double. 
                    // Literal() mantiene el tipo para que Expression.eq no falle.
                    val value: Any = when {
                        prim.isNumber -> prim.asNumber
                        prim.isBoolean -> prim.asBoolean
                        else -> prim.asString
                    }
                    filterConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                }
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
     * Helper robusto para construir la referencia Prov:Mun:Agg:Zon:Pol:Par:Rec
     */
    private fun extractSigpacRef(feature: org.maplibre.geojson.Feature): String {
        fun getSafe(key: String): String {
            if (!feature.hasProperty(key)) return "0"
            val p = feature.getProperty(key).asJsonPrimitive
            return when {
                p.isNumber -> p.asNumber.toLong().toString() // Forzamos a Long para evitar .0
                else -> p.asString.replace("\"", "")
            }
        }

        val prov = getSafe("provincia")
        val mun = getSafe("municipio")
        if (prov == "0" || mun == "0") return ""

        return "$prov:$mun:${getSafe("agregado")}:${getSafe("zona")}:${getSafe("poligono")}:${getSafe("parcela")}:${getSafe("recinto")}"
    }

    /**
     * Obtiene datos extendidos con estrategia de Fallback (API -> PBF)
     */
    suspend fun fetchExtendedData(map: MapLibreMap): Pair<Map<String, String>?, Map<String, String>?> {
        if (map.cameraPosition.zoom < 13.5) return Pair(null, null)
        
        val center = map.cameraPosition.target ?: return Pair(null, null)
        
        // 1. Intentar API (Prioridad: Datos oficiales actualizados)
        val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
        
        // 2. Fallback a Vector Tiles (Si la API falla o estamos offline)
        val recintoData = fullData ?: run {
            val screenPoint = map.projection.toScreenLocation(center)
            val features = map.queryRenderedFeatures(RectF(screenPoint.x-5f, screenPoint.y-5f, screenPoint.x+5f, screenPoint.y+5f), LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val f = features[0]
                mutableMapOf<String, String>().apply {
                    f.properties()?.entrySet()?.forEach { 
                        this[it.key] = it.value.toString().replace("\"", "") 
                    }
                }
            } else null
        }

        // 3. Cultivo (Siempre desde PBF ya que suele estar en una capa temática separada)
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
                
                // Traducción dinámica de códigos
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
