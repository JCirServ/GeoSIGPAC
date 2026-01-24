
package com.geosigpac.cirserv.ui.map

import android.graphics.RectF
import android.util.Log
import com.geosigpac.cirserv.ui.*
import com.geosigpac.cirserv.utils.SigpacCodeManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer

object MapLogic {

    fun updateRealtimeInfo(map: MapLibreMap): String {
        if (map.cameraPosition.zoom < 13) {
            val emptyFilter = Expression.literal(false)
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as FillLayer).setFilter(emptyFilter) }
            // Limpiar capa offset
            map.style?.getLayer("${LAYER_RECINTO_HIGHLIGHT_LINE}_offset")?.let { (it as FillLayer).setFilter(emptyFilter) }
            return ""
        }
        val center = map.cameraPosition.target ?: return ""
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        
        try {
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val feature = features[0]
                val prov = feature.getStringProperty("provincia")
                val mun = feature.getStringProperty("municipio")
                val agg = feature.getStringProperty("agregado") ?: "0"
                val zon = feature.getStringProperty("zona") ?: "0"
                val pol = feature.getStringProperty("poligono")
                val parc = feature.getStringProperty("parcela")
                val rec = feature.getStringProperty("recinto")
                
                var resultRef = ""
                if (prov != null && mun != null && pol != null && parc != null && rec != null) {
                    resultRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
                }
                
                val props = feature.properties()
                if (props != null) {
                    val filterConditions = mutableListOf<Expression>()
                    SIGPAC_KEYS.forEach { key ->
                        if (props.has(key)) {
                            val element = props.get(key)
                            val value: Any = when {
                                element.isJsonPrimitive -> {
                                    val prim = element.asJsonPrimitive
                                    when {
                                        prim.isNumber -> prim.asNumber
                                        prim.isBoolean -> prim.asBoolean
                                        else -> prim.asString
                                    }
                                }
                                else -> element.toString()
                            }
                            filterConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                        }
                    }
                    val finalFilter = Expression.all(*filterConditions.toTypedArray())
                    
                    // Aplicar filtro a todas las capas de resaltado
                    map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(finalFilter) }
                    map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as FillLayer).setFilter(finalFilter) }
                    map.style?.getLayer("${LAYER_RECINTO_HIGHLIGHT_LINE}_offset")?.let { (it as FillLayer).setFilter(finalFilter) }
                }
                return resultRef
            } else {
                val emptyFilter = Expression.literal(false)
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as FillLayer).setFilter(emptyFilter) }
                map.style?.getLayer("${LAYER_RECINTO_HIGHLIGHT_LINE}_offset")?.let { (it as FillLayer).setFilter(emptyFilter) }
                return ""
            }
        } catch (e: Exception) { 
            Log.e(TAG_MAP, "Error realtime update: ${e.message}") 
            return ""
        }
    }

    suspend fun fetchExtendedData(map: MapLibreMap): Pair<Map<String, String>?, Map<String, String>?> {
        if (map.cameraPosition.zoom < 13) return Pair(null, null)
        
        val center = map.cameraPosition.target ?: return Pair(null, null)
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        
        var recinto: Map<String, String>? = null
        var cultivo: Map<String, String>? = null

        try {
            // 1. Recinto (Completo desde API)
            val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
            if (fullData != null) {
                recinto = fullData
            } else {
                // Fallback a Vector Tiles
                val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
                if (features.isNotEmpty()) {
                    val props = features[0].properties()
                    recinto = mapOf(
                        "provincia" to (props?.get("provincia")?.asString ?: ""),
                        "municipio" to (props?.get("municipio")?.asString ?: ""),
                        "poligono" to (props?.get("poligono")?.asString ?: ""),
                        "parcela" to (props?.get("parcela")?.asString ?: ""),
                        "recinto" to (props?.get("recinto")?.asString ?: ""),
                        "agregado" to (props?.get("agregado")?.asString ?: "0"),
                        "zona" to (props?.get("zona")?.asString ?: "0"),
                        "superficie" to (props?.get("superficie")?.toString() ?: "0"),
                        "uso_sigpac" to "Cargando...",
                        "incidencias" to ""
                    )
                }
            }

            // 2. Cultivo (Vector Tiles)
            val cultFeatures = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL)
            if (cultFeatures.isNotEmpty()) {
                 // LÓGICA DE SELECCIÓN POR MAYOR Nº EXPEDIENTE
                 val bestFeature = cultFeatures.maxByOrNull { feat ->
                     val p = feat.properties()
                     val rawExp = p?.get("exp_num")
                     when {
                         rawExp == null -> 0L
                         rawExp.isJsonPrimitive && rawExp.asJsonPrimitive.isNumber -> rawExp.asJsonPrimitive.asLong
                         rawExp.isJsonPrimitive && rawExp.asJsonPrimitive.isString -> {
                             // Intentamos limpiar y parsear si viene como string
                             rawExp.asJsonPrimitive.asString.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                         }
                         else -> 0L
                     }
                 } ?: cultFeatures[0]

                 val props = bestFeature.properties()
                 if (props != null) {
                    val mapProps = mutableMapOf<String, String>()
                    props.entrySet().forEach { mapProps[it.key] = it.value.toString().replace("\"", "") }
                    
                    val rawAprovecha = mapProps["tipo_aprovecha"]
                    val translatedAprovecha = SigpacCodeManager.getAprovechamientoDescription(rawAprovecha)
                    mapProps["tipo_aprovecha"] = translatedAprovecha ?: rawAprovecha ?: "-"
                    
                    val rawProd = mapProps["parc_producto"]
                    val translatedProd = SigpacCodeManager.getProductoDescription(rawProd)
                    mapProps["parc_producto"] = translatedProd ?: rawProd ?: "-"
                    
                    val rawProdSec = mapProps["cultsecun_producto"]
                    val translatedProdSec = SigpacCodeManager.getProductoDescription(rawProdSec)
                    mapProps["cultsecun_producto"] = translatedProdSec ?: rawProdSec ?: "-"
                    
                    cultivo = mapProps
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG_MAP, "Error querying extended features: ${e.message}") 
        }
        return Pair(recinto, cultivo)
    }
}
