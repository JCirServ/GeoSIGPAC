
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
    styleBuilder.withLayer(RasterLayer(LAYER_BASE, SOURCE_BASE))

    map.setStyle(styleBuilder) { style ->
        
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetCultivo = TileSet("pbf", cultivoUrl)
                tileSetCultivo.minZoom = 5f; tileSetCultivo.maxZoom = 15f
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillAntialias(false) // Previene lineas en cultivos también
                )
                style.addLayer(fillLayer)
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 5f; tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // CAPA 1: RELLENO (TINT)
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val tintLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                tintLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                tintLayer.setProperties(
                    PropertyFactory.fillColor(tintColor.toArgb()),
                    PropertyFactory.fillOpacity(0.15f),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillAntialias(false) // CRUCIAL: Elimina la cuadrícula estática
                )
                style.addLayer(tintLayer)

                // CAPA 2: BORDES SIMULADOS (GROSOR MEDIANTE OFFSET ESCALADO)
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM
                
                // 2A. Borde Principal
                val borderLayerMain = FillLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                borderLayerMain.sourceLayer = SOURCE_LAYER_ID_RECINTO
                borderLayerMain.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(borderColor.toArgb()),
                    PropertyFactory.fillAntialias(true)
                )
                style.addLayer(borderLayerMain)

                // 2B. Borde Secundario (Simula grosor variable con zoom)
                val borderLayerOffset = FillLayer("${LAYER_RECINTO_LINE}_offset", SOURCE_RECINTO)
                borderLayerOffset.sourceLayer = SOURCE_LAYER_ID_RECINTO
                borderLayerOffset.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(borderColor.copy(alpha = 0.6f).toArgb()),
                    PropertyFactory.fillTranslate(
                        Expression.interpolate(
                            Expression.exponential(1.5f),
                            Expression.zoom(),
                            Expression.stop(10, arrayOf(0.3f, 0.3f)),
                            Expression.stop(13, arrayOf(0.7f, 0.7f)),
                            Expression.stop(16, arrayOf(1.5f, 1.5f)),
                            Expression.stop(19, arrayOf(3f, 3f))
                        )
                    ),
                    PropertyFactory.fillAntialias(true)
                )
                style.addLayer(borderLayerOffset)


                // CAPAS DE RESALTADO (SELECCIÓN)
                val initialFilter = Expression.literal(false)

                // Resaltado Relleno
                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.setFilter(initialFilter)
                highlightFill.setProperties(
                    PropertyFactory.fillColor(HighlightColor.toArgb()),
                    PropertyFactory.fillOpacity(0.5f), 
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(highlightFill)

                // Resaltado Borde Principal
                val highlightLineMain = FillLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO)
                highlightLineMain.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLineMain.setFilter(initialFilter)
                highlightLineMain.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(HighlightColor.toArgb()),
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(true)
                )
                style.addLayer(highlightLineMain)

                // Resaltado Borde Secundario (Simula grosor variable con zoom)
                val highlightLineOffset = FillLayer("${LAYER_RECINTO_HIGHLIGHT_LINE}_offset", SOURCE_RECINTO)
                highlightLineOffset.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLineOffset.setFilter(initialFilter)
                highlightLineOffset.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(HighlightColor.copy(alpha = 0.6f).toArgb()),
                    PropertyFactory.fillTranslate(
                        Expression.interpolate(
                            Expression.exponential(1.5f),
                            Expression.zoom(),
                            Expression.stop(10, arrayOf(0.3f, 0.3f)),
                            Expression.stop(13, arrayOf(0.7f, 0.7f)),
                            Expression.stop(16, arrayOf(1.5f, 1.5f)),
                            Expression.stop(19, arrayOf(3f, 3f))
                        )
                    ),
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(true)
                )
                style.addLayer(highlightLineOffset)
                
            } catch (e: Exception) { e.printStackTrace() }
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

// Extension function helper
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
