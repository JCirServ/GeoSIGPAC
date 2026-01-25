
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
    // 1. Cargar configuración fina desde JSON (Recintos)
    val styleBuilder = Style.Builder().fromUri("asset://recinto_style.json")

    map.setStyle(styleBuilder) { style ->
        
        // 2. Añadir Mapa Base (Raster) POR DEBAJO (index 0)
        val tileUrl = if (baseMap == BaseMap.OSM) {
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        } else {
            "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
        }

        val tileSet = TileSet("2.2.0", tileUrl)
        tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
        
        val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
        style.addSource(rasterSource)
        style.addLayerAt(RasterLayer(LAYER_BASE, SOURCE_BASE), 0)

        // 3. Gestión de Visibilidad Recinto (cargado por JSON)
        if (!showRecinto) {
            style.getLayer(LAYER_RECINTO_FILL)?.setProperties(PropertyFactory.visibility(Property.NONE))
            style.getLayer(LAYER_RECINTO_LINE)?.setProperties(PropertyFactory.visibility(Property.NONE))
        } else {
            style.getLayer(LAYER_RECINTO_FILL)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            style.getLayer(LAYER_RECINTO_LINE)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
        }

        // 4. Capa Cultivo (Programático)
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetCultivo = TileSet("pbf", cultivoUrl)
                tileSetCultivo.minZoom = 5f
                tileSetCultivo.maxZoom = 15f
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                // Insertar debajo de relleno recinto si existe, para que el borde del recinto quede encima
                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillAntialias(true),
                    PropertyFactory.fillTranslate(arrayOf(0f, 0f))
                )
                
                if (style.getLayer(LAYER_RECINTO_FILL) != null) {
                    style.addLayerBelow(fillLayer, LAYER_RECINTO_FILL)
                } else {
                    style.addLayer(fillLayer)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 5. Capas de Resaltado (Programático) - Dependen de SOURCE_RECINTO (recinto-source)
        try {
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
