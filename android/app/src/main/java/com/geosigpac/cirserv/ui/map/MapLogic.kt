
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

    /**
     * Actualiza la información básica bajo el puntero central en tiempo real.
     * Devuelve la referencia SIGPAC formateada (Prov:Mun:Agg:Zon:Pol:Par:Rec) o cadena vacía.
     */
    fun updateRealtimeInfo(map: MapLibreMap): String {
        // Optimización: No consultar si el zoom es muy lejano
        if (map.cameraPosition.zoom < 13.5) {
            return ""
        }
        
        try {
            val center = map.cameraPosition.target ?: return ""
            val screenPoint = map.projection.toScreenLocation(center)
            // Aumentamos el área de toque (+/- 20px) para facilitar la detección al mover rápido
            val searchArea = RectF(screenPoint.x - 20f, screenPoint.y - 20f, screenPoint.x + 20f, screenPoint.y + 20f)
            
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val feature = features[0]
                
                // Helper para extracción segura de propiedades (Maneja Strings y Numbers)
                fun getSafeProp(key: String, default: String = "0"): String {
                    if (!feature.hasProperty(key)) return default
                    val prop = feature.getProperty(key)
                    return when {
                        prop.isJsonPrimitive -> {
                            val prim = prop.asJsonPrimitive
                            when {
                                prim.isString -> prim.asString
                                prim.isNumber -> prim.asNumber.toString().replace(".0", "") // Integers sin decimal
                                else -> prim.toString()
                            }
                        }
                        else -> prop.toString()
                    }
                }

                val prov = getSafeProp("provincia", "")
                val mun = getSafeProp("municipio", "")
                val agg = getSafeProp("agregado", "0")
                val zon = getSafeProp("zona", "0")
                val pol = getSafeProp("poligono", "0")
                val parc = getSafeProp("parcela", "0")
                val rec = getSafeProp("recinto", "0")
                
                var resultRef = ""
                if (prov.isNotEmpty() && mun.isNotEmpty()) {
                    resultRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
                }
                
                // Resaltar el recinto seleccionado
                val filterConditions = mutableListOf<Expression>()
                SIGPAC_KEYS.forEach { key ->
                    if (feature.hasProperty(key)) {
                        val prop = feature.getProperty(key)
                        // Para el filtro Expression, necesitamos mantener el tipo original (Number o String)
                        val value: Any = when {
                            prop.isJsonPrimitive -> {
                                val prim = prop.asJsonPrimitive
                                when {
                                    prim.isNumber -> prim.asNumber
                                    prim.isBoolean -> prim.asBoolean
                                    else -> prim.asString
                                }
                            }
                            else -> prop.toString()
                        }
                        filterConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                    }
                }
                
                if (filterConditions.isNotEmpty()) {
                    val finalFilter = Expression.all(*filterConditions.toTypedArray())
                    map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(finalFilter) }
                    map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(finalFilter) }
                }

                return resultRef
            } else {
                // Limpiar resaltado si no hay nada debajo
                val emptyFilter = Expression.literal(false)
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
                return ""
            }
        } catch (e: Exception) { 
            // Log.e(TAG_MAP, "Error realtime update: ${e.message}") 
            return ""
        }
    }

    /**
     * Obtiene datos extendidos (Uso, Superficie, Cultivo) cuando el mapa se detiene.
     * Intenta API primero, luego fallback a datos vectoriales del mapa.
     */
    suspend fun fetchExtendedData(map: MapLibreMap): Pair<Map<String, String>?, Map<String, String>?> {
        if (map.cameraPosition.zoom < 13.5) return Pair(null, null)
        
        try {
            val center = map.cameraPosition.target ?: return Pair(null, null)
            val screenPoint = map.projection.toScreenLocation(center)
            val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
            
            var recinto: Map<String, String>? = null
            var cultivo: Map<String, String>? = null

            // 1. Recinto (Completo desde API)
            val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
            if (fullData != null) {
                recinto = fullData
            } else {
                // FALLBACK: Extraer datos de las Vector Tiles (si no hay red o falla API)
                val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
                if (features.isNotEmpty()) {
                    val f = features[0]
                    
                    fun getSafe(key: String, def: String = ""): String {
                        if (!f.hasProperty(key)) return def
                        val p = f.getProperty(key)
                        return if (p.isJsonPrimitive && p.asJsonPrimitive.isNumber) 
                            p.asJsonPrimitive.asNumber.toString().replace(".0", "")
                        else p.toString().replace("\"", "")
                    }

                    recinto = mapOf(
                        "provincia" to getSafe("provincia"),
                        "municipio" to getSafe("municipio"),
                        "poligono" to getSafe("poligono"),
                        "parcela" to getSafe("parcela"),
                        "recinto" to getSafe("recinto"),
                        "agregado" to getSafe("agregado", "0"),
                        "zona" to getSafe("zona", "0"),
                        "superficie" to getSafe("superficie", "0"),
                        "uso_sigpac" to "Cargando...", // Uso a veces no está en tiles
                        "incidencias" to ""
                    )
                }
            }

            // 2. Cultivo (Vector Tiles)
            // Intentamos obtener info del cultivo declarado visualizado
            try {
                val cultFeatures = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL)
                if (cultFeatures.isNotEmpty()) {
                     // Elegir el cultivo más reciente si hay solapamientos (heurística simple por ahora: el primero renderizado)
                     val bestFeature = cultFeatures[0]
                     val props = bestFeature.properties()
                     
                     if (props != null) {
                        val mapProps = mutableMapOf<String, String>()
                        
                        // Extraer todas las propiedades de forma segura
                        props.entrySet().forEach { 
                            mapProps[it.key] = it.value.toString().replace("\"", "") 
                        }
                        
                        // Traducir códigos a descripciones legibles
                        val rawAprovecha = mapProps["tipo_aprovecha"]
                        mapProps["tipo_aprovecha"] = SigpacCodeManager.getAprovechamientoDescription(rawAprovecha) ?: rawAprovecha ?: "-"
                        
                        val rawProd = mapProps["parc_producto"]
                        mapProps["parc_producto"] = SigpacCodeManager.getProductoDescription(rawProd) ?: rawProd ?: "-"
                        
                        val rawProdSec = mapProps["cultsecun_producto"]
                        mapProps["cultsecun_producto"] = SigpacCodeManager.getProductoDescription(rawProdSec) ?: rawProdSec ?: "-"
                        
                        cultivo = mapProps
                    }
                }
            } catch (e: Exception) { Log.w(TAG_MAP, "Cultivo feature query failed: ${e.message}") }

            return Pair(recinto, cultivo)
        } catch (e: Exception) { 
            Log.e(TAG_MAP, "Error querying extended features: ${e.message}") 
            return Pair(null, null)
        }
    }
}
