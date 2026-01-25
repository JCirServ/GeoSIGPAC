
package com.geosigpac.cirserv.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object MapLayers {

    fun setupProjectLayers(context: Context, map: MapLibreMap) {
        map.getStyle { style ->
            // --- CAPAS DE PARCELAS KML (Relleno y Línea) ---
            if (style.getSource(SOURCE_PROJECTS) == null) {
                style.addSource(GeoJsonSource(SOURCE_PROJECTS))
                
                // Color KML: Índigo / Violeta (Contrasta con Amarillo Cultivo y Cian Recinto)
                val kmlFillColor = Color(0xFF6200EA) // Deep Purple A700
                val kmlLineColor = Color(0xFF00E5FF) // Cyan Accent
                
                // Relleno Parcela
                style.addLayer(FillLayer(LAYER_PROJECTS_FILL, SOURCE_PROJECTS).apply {
                    setProperties(
                        PropertyFactory.fillColor(kmlFillColor.toArgb()), 
                        PropertyFactory.fillOpacity(0.35f)
                    )
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("parcela")))
                })
                
                // Línea Parcela
                style.addLayer(LineLayer(LAYER_PROJECTS_LINE, SOURCE_PROJECTS).apply {
                    setProperties(
                        PropertyFactory.lineColor(kmlLineColor.toArgb()), 
                        PropertyFactory.lineWidth(2.0f)
                    )
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("parcela")))
                })
                
                // Centroide (Punto)
                style.addLayer(CircleLayer(LAYER_PROJECTS_CENTROID, SOURCE_PROJECTS).apply {
                    setProperties(
                        PropertyFactory.circleColor(Color(0xFF00FF88).toArgb()),
                        PropertyFactory.circleRadius(4f),
                        PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                        PropertyFactory.circleStrokeWidth(1.5f)
                    )
                    setFilter(Expression.eq(Expression.get("type"), Expression.literal("centroid")))
                })
            }

            // --- CAPA DE FOTOS ---
            if (style.getSource(SOURCE_PHOTOS) == null) {
                val iconBitmap = generatePhotoMarkerBitmap()
                style.addImage(ICON_PHOTO, iconBitmap)

                style.addSource(GeoJsonSource(SOURCE_PHOTOS))
                
                style.addLayer(SymbolLayer(LAYER_PHOTOS, SOURCE_PHOTOS).apply {
                    setProperties(
                        PropertyFactory.iconImage(ICON_PHOTO),
                        PropertyFactory.iconSize(1.0f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconOffset(arrayOf(0f, -10f)) 
                    )
                })
            }

            // --- CAPA DE BÚSQUEDA ---
            if (style.getSource(SOURCE_SEARCH_RESULT) == null) {
                style.addSource(GeoJsonSource(SOURCE_SEARCH_RESULT))
                style.addLayer(FillLayer(LAYER_SEARCH_RESULT_FILL, SOURCE_SEARCH_RESULT).apply { setProperties(PropertyFactory.fillColor(HighlightColor.toArgb()), PropertyFactory.fillOpacity(0.4f)) })
                style.addLayer(LineLayer(LAYER_SEARCH_RESULT_LINE, SOURCE_SEARCH_RESULT).apply { setProperties(PropertyFactory.lineColor(HighlightColor.toArgb()), PropertyFactory.lineWidth(4f)) })
            }
        }
    }

    suspend fun updateProjectsLayer(map: MapLibreMap, currentExpedientes: List<NativeExpediente>, visibleIds: Set<String>) = withContext(Dispatchers.Default) {
        val parcelFeatures = mutableListOf<Feature>()
        val photoFeatures = mutableListOf<Feature>()
        
        currentExpedientes.filter { visibleIds.contains(it.id) }.forEach { exp ->
            exp.parcelas.forEach { p ->
                // 1. GEOMETRÍA PARCELA
                if (p.geometryRaw != null) {
                    val raw = p.geometryRaw.trim()
                    if (raw.startsWith("{")) {
                        try {
                            val featureJson = """{"type": "Feature", "properties": {"type": "parcela", "ref": "${p.referencia}"}, "geometry": $raw}"""
                            parcelFeatures.add(Feature.fromJson(featureJson))
                        } catch(e: Exception) {}
                    } else {
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
                                if (points.first() != points.last()) points.add(points.first())
                                val polygon = Polygon.fromLngLats(listOf(points))
                                val feat = Feature.fromGeometry(polygon)
                                feat.addStringProperty("type", "parcela")
                                feat.addStringProperty("ref", p.referencia)
                                parcelFeatures.add(feat)
                            }
                        } catch (e: Exception) {}
                    }
                }

                // 2. CENTROIDE
                if (p.centroidLat != null && p.centroidLng != null) {
                    val centerPoint = Point.fromLngLat(p.centroidLng, p.centroidLat)
                    val feat = Feature.fromGeometry(centerPoint)
                    feat.addStringProperty("type", "centroid")
                    feat.addStringProperty("ref", p.referencia)
                    parcelFeatures.add(feat)
                }

                // 3. FOTOS
                p.photos.forEach { uri ->
                    val locString = p.photoLocations[uri]
                    if (locString != null) {
                        try {
                            val parts = locString.split(",")
                            if (parts.size >= 2) {
                                val lat = parts[0].toDouble()
                                val lng = parts[1].toDouble()
                                val photoPoint = Point.fromLngLat(lng, lat)
                                val photoFeat = Feature.fromGeometry(photoPoint)
                                
                                photoFeat.addStringProperty("type", "photo")
                                photoFeat.addStringProperty("uri", uri)
                                photoFeat.addStringProperty("parcelId", p.id)
                                photoFeat.addStringProperty("expId", exp.id)
                                
                                photoFeatures.add(photoFeat)
                            }
                        } catch (e: Exception) {
                            Log.e("MapLayers", "Error parsing photo location: $locString")
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (map.style != null) {
                map.style?.getSourceAs<GeoJsonSource>(SOURCE_PROJECTS)?.setGeoJson(FeatureCollection.fromFeatures(parcelFeatures))
                map.style?.getSourceAs<GeoJsonSource>(SOURCE_PHOTOS)?.setGeoJson(FeatureCollection.fromFeatures(photoFeatures))
            }
        }
    }

    private fun generatePhotoMarkerBitmap(): Bitmap {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = android.graphics.Color.parseColor("#444444")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val radius = size / 2f - 4f
        val cx = size / 2f
        val cy = size / 2f
        
        canvas.drawCircle(cx, cy, radius, paintBg)
        canvas.drawCircle(cx, cy, radius, paintBorder)

        val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = android.graphics.Color.parseColor("#333333") 
            style = Paint.Style.FILL
        }

        val rectW = size * 0.5f; val rectH = size * 0.35f
        val left = cx - rectW/2; val top = cy - rectH/2 + 4f
        canvas.drawRoundRect(RectF(left, top, left + rectW, top + rectH), 6f, 6f, paintIcon)
        val visorW = size * 0.2f; val visorH = size * 0.1f
        canvas.drawRect(RectF(cx - visorW/2, top - visorH, cx + visorW/2, top), paintIcon)

        paintIcon.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx, cy + 4f, size * 0.12f, paintIcon)
        val paintLensBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#333333"); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy + 4f, size * 0.12f, paintLensBorder)

        return bitmap
    }
}
