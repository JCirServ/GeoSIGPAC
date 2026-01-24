
package com.geosigpac.cirserv.ui.map

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object MapLayers {

    fun setupProjectLayers(map: MapLibreMap) {
        map.getStyle { style ->
            if (style.getSource(SOURCE_PROJECTS) == null) {
                style.addSource(GeoJsonSource(SOURCE_PROJECTS))
                
                // 1. Relleno Parcela (Polígonos)
                style.addLayer(FillLayer(LAYER_PROJECTS_FILL, SOURCE_PROJECTS).apply {
                    setProperties(PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), PropertyFactory.fillOpacity(0.3f))
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("parcela")))
                })
                
                // 2. Línea Parcela (Polígonos)
                style.addLayer(LineLayer(LAYER_PROJECTS_LINE, SOURCE_PROJECTS).apply {
                    setProperties(PropertyFactory.lineColor(Color(0xFF0D47A1).toArgb()), PropertyFactory.lineWidth(1.5f))
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("parcela")))
                })
                
                // 3. Puntos Pendientes / Provisionales
                // Se renderizan como círculos grandes del mismo color que el relleno de polígono para indicar "esto será un recinto"
                style.addLayer(CircleLayer("projects-pending-point", SOURCE_PROJECTS).apply {
                    setProperties(
                        PropertyFactory.circleColor(Color(0xFF2196F3).toArgb()), // Mismo azul que el relleno
                        PropertyFactory.circleOpacity(0.6f),
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleStrokeColor(Color(0xFF0D47A1).toArgb()),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("pending_point")))
                })

                // 4. Centroide (Punto Informativo)
                style.addLayer(CircleLayer(LAYER_PROJECTS_CENTROID, SOURCE_PROJECTS).apply {
                    setProperties(
                        PropertyFactory.circleColor(Color(0xFF00FF88).toArgb()),
                        PropertyFactory.circleRadius(5f),
                        PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("centroid")))
                })
            }

            if (style.getSource(SOURCE_SEARCH_RESULT) == null) {
                style.addSource(GeoJsonSource(SOURCE_SEARCH_RESULT))
                style.addLayer(FillLayer(LAYER_SEARCH_RESULT_FILL, SOURCE_SEARCH_RESULT).apply { setProperties(PropertyFactory.fillColor(HighlightColor.toArgb()), PropertyFactory.fillOpacity(0.4f)) })
                style.addLayer(LineLayer(LAYER_SEARCH_RESULT_LINE, SOURCE_SEARCH_RESULT).apply { setProperties(PropertyFactory.lineColor(HighlightColor.toArgb()), PropertyFactory.lineWidth(4f)) })
            }
        }
    }

    suspend fun updateProjectsLayer(map: MapLibreMap, currentExpedientes: List<NativeExpediente>, visibleIds: Set<String>) = withContext(Dispatchers.Default) {
        val features = mutableListOf<Feature>()
        
        currentExpedientes.filter { visibleIds.contains(it.id) }.forEach { exp ->
            exp.parcelas.forEach { p ->
                // 1. Geometría (Polygon/MultiPolygon o Point Provisional)
                if (p.geometryRaw != null) {
                    val raw = p.geometryRaw.trim()
                    
                    // A. GeoJSON Directo (Hidratado o Punto Provisional)
                    if (raw.startsWith("{")) {
                        try {
                            // Detectamos si es un Punto Provisional
                            val isPoint = raw.contains("\"type\": \"Point\"", ignoreCase = true) || raw.contains("\"type\":\"Point\"", ignoreCase = true)
                            val type = if (isPoint) "pending_point" else "parcela"

                            val featureJson = """
                                {
                                    "type": "Feature", 
                                    "properties": {
                                        "type": "$type",
                                        "ref": "${p.referencia}"
                                    }, 
                                    "geometry": $raw
                                }
                            """.trimIndent()
                            
                            val feat = Feature.fromJson(featureJson)
                            features.add(feat)
                        } catch(e: Exception) {
                            Log.e(TAG_MAP, "Error parsing GeoJSON geometry for ${p.referencia}: ${e.message}")
                        }
                    } 
                    // B. Formato KML Legado ("lat,lng lat,lng")
                    else {
                        try {
                            val points = mutableListOf<Point>()
                            val coordPairs = raw.split("\\s+".toRegex())
                            coordPairs.forEach { pair ->
                                val coords = pair.split(",")
                                if (coords.size >= 2) {
                                    val lng = coords[0].toDoubleOrNull()
                                    val lat = coords[1].toDoubleOrNull()
                                    if (lng != null && lat != null) points.add(Point.fromLngLat(lng, lat))
                                }
                            }
                            if (points.isNotEmpty()) {
                                if (points.first() != points.last()) {
                                    points.add(points.first())
                                }
                                val polygon = Polygon.fromLngLats(listOf(points))
                                val feat = Feature.fromGeometry(polygon)
                                feat.addStringProperty("type", "parcela")
                                feat.addStringProperty("ref", p.referencia)
                                features.add(feat)
                            }
                        } catch (e: Exception) {}
                    }
                }

                // 2. Punto del Centroide oficial (si no es un punto provisional, para no duplicar dots)
                if (p.centroidLat != null && p.centroidLng != null) {
                    val isPendingPoint = p.geometryRaw != null && (p.geometryRaw.contains("Point", ignoreCase = true))
                    if (!isPendingPoint) {
                        val centerPoint = Point.fromLngLat(p.centroidLng, p.centroidLat)
                        val feat = Feature.fromGeometry(centerPoint)
                        feat.addStringProperty("type", "centroid")
                        feat.addStringProperty("ref", p.referencia)
                        features.add(feat)
                    }
                }
            }
        }

        val collection = FeatureCollection.fromFeatures(features)
        withContext(Dispatchers.Main) {
            if (map.style != null) {
                map.style?.getSourceAs<GeoJsonSource>(SOURCE_PROJECTS)?.setGeoJson(collection)
            }
        }
    }
}
