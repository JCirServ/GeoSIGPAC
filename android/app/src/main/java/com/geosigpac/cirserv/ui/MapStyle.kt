
package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

fun loadMapStyle(
    map: MapLibreMap,
    baseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    context: Context,
    shouldCenterUser: Boolean,
    onLocationEnabled: () -> Unit
) {
    // Configuración inicial del Builder sin cargar JSON externo para tener control total
    val styleBuilder = Style.Builder()

    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    val tileSet = TileSet("2.2.0", tileUrl)
    tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
    
    val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
    styleBuilder.withSource(rasterSource)
    
    // IMPORTANTE: Añadir antialias al raster base para suavizar la imagen de fondo
    val rasterLayer = RasterLayer(LAYER_BASE, SOURCE_BASE).apply {
        setProperties(
            PropertyFactory.rasterOpacity(1.0f),
            PropertyFactory.rasterAntialias(true)
        )
    }
    styleBuilder.withLayer(rasterLayer)

    map.setStyle(styleBuilder) { style ->
        
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetCultivo = TileSet("pbf", cultivoUrl)
                tileSetCultivo.minZoom = 5f
                tileSetCultivo.maxZoom = 18f // Aumentar para mejor overzoom
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillAntialias(true),
                    PropertyFactory.fillTranslate(arrayOf(0f, 0f)),
                    // Evitar gaps en los bordes del cultivo
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb())
                )
                style.addLayer(fillLayer)
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 5f
                tileSetRecinto.maxZoom = 18f // Aumentar para overzoom
                
                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // SOLUCIÓN GAPS: Usar LineLayer con configuración especial para los bordes
                
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM
                
                // CAPA 1: LÍNEA PRINCIPAL (borde visible)
                val lineLayer = LineLayer("${LAYER_RECINTO_LINE}_main", SOURCE_RECINTO)
                lineLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                lineLayer.setProperties(
                    PropertyFactory.lineColor(borderColor.toArgb()),
                    PropertyFactory.lineOpacity(0.95f),
                    // GROSOR DINÁMICO
                    PropertyFactory.lineWidth(
                        Expression.interpolate(
                            Expression.exponential(1.5f),
                            Expression.zoom(),
                            Expression.stop(5f, 0.8f),
                            Expression.stop(10f, 1.2f),
                            Expression.stop(13f, 2.0f),
                            Expression.stop(15f, 3.0f),
                            Expression.stop(17f, 5.0f),
                            Expression.stop(20f, 8.0f),
                            Expression.stop(22f, 12.0f)
                        )
                    ),
                    // CONFIGURACIÓN ANTI-GAPS
                    PropertyFactory.lineTranslate(arrayOf(0f, 0f)),
                    PropertyFactory.lineOffset(0f),
                    PropertyFactory.lineBlur(0.2f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.linePattern(Expression.literal("")),
                    PropertyFactory.lineGapWidth(0f),
                    PropertyFactory.lineSortKey(1.0f)
                )
                style.addLayer(lineLayer)
                
                // CAPA 2: LÍNEA DE REFUERZO (Overlay para cubrir gaps residuales)
                val lineLayerOverlay = LineLayer("${LAYER_RECINTO_LINE}_overlay", SOURCE_RECINTO)
                lineLayerOverlay.sourceLayer = SOURCE_LAYER_ID_RECINTO
                lineLayerOverlay.setProperties(
                    PropertyFactory.lineColor(borderColor.toArgb()),
                    PropertyFactory.lineOpacity(0.3f), // Transparencia alta
                    PropertyFactory.lineWidth(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(5f, 0.5f),
                            Expression.stop(15f, 1.0f),
                            Expression.stop(22f, 2.0f)
                        )
                    ),
                    // Pequeño offset para cubrir las uniones entre tiles
                    PropertyFactory.lineTranslate(arrayOf(0.3f, 0.3f)),
                    PropertyFactory.lineBlur(0.5f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                )
                // Solo mostrar en zooms altos donde aparecen los gaps
                lineLayerOverlay.setFilter(Expression.gte(Expression.zoom(), 15f))
                style.addLayer(lineLayerOverlay)

                // CAPA 3: RELLENO (Fill)
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val fillLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(tintColor.toArgb()),
                    PropertyFactory.fillOpacity(0.15f),
                    PropertyFactory.fillAntialias(true),
                    PropertyFactory.fillTranslate(arrayOf(0f, 0f))
                )
                // Colocar el relleno DEBAJO de las líneas
                style.addLayerBelow(fillLayer, "${LAYER_RECINTO_LINE}_main")

                // CAPAS DE HIGHLIGHT (Selección)
                val initialFilter = Expression.literal(false)

                // Highlight Fill
                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.setFilter(initialFilter)
                highlightFill.setProperties(
                    PropertyFactory.fillColor(HighlightColor.toArgb()),
                    PropertyFactory.fillOpacity(0.3f),
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(true),
                    PropertyFactory.fillTranslate(arrayOf(0f, 0f))
                )
                style.addLayer(highlightFill)

                // Highlight Line
                val highlightLine = LineLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO)
                highlightLine.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLine.setFilter(initialFilter)
                highlightLine.setProperties(
                    PropertyFactory.lineColor(HighlightColor.toArgb()),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.lineWidth(
                        Expression.interpolate(
                            Expression.exponential(1.5f),
                            Expression.zoom(),
                            Expression.stop(10f, 3.0f),
                            Expression.stop(15f, 5.0f),
                            Expression.stop(20f, 8.0f),
                            Expression.stop(22f, 12.0f)
                        )
                    ),
                    PropertyFactory.lineTranslate(arrayOf(0f, 0f))
                )
                style.addLayer(highlightLine)
                
            } catch (e: Exception) { e.printStackTrace() }
        }

        // CONFIGURACIÓN ADICIONAL 11.5.1
        try {
            // Mejoras de rendimiento y renderizado
            map.setMaximumFps(30) // Limitar FPS para dar tiempo al renderizado de calidad
            map.setPrefetchZoomDelta(2) // Pre-cargar tiles
            style.styleDefaultCamera = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (enableLocation(map, context, shouldCenterUser)) {
            onLocationEnabled()
        }
    }
}

@SuppressLint("MissingPermission")
fun enableLocation(map: MapLibreMap?, context: Context, shouldCenter: Boolean): Boolean {
    if (map == null || map.style == null) return false

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        try {
            val locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(context, map.style!!)
                .useDefaultLocationEngine(true)
                .build()
            
            locationComponent.activateLocationComponent(options)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS

            if (shouldCenter) {
                locationComponent.cameraMode = CameraMode.TRACKING
                locationComponent.zoomWhileTracking(USER_TRACKING_ZOOM)
            } else {
                locationComponent.cameraMode = CameraMode.NONE
            }
            return true
        } catch (e: Exception) { e.printStackTrace() }
    }
    return false
}

fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
