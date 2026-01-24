
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
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.BackgroundLayer
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

    // 1. CAPA DE FONDO: Clave para ocultar las rendijas blancas del mapa base
    styleBuilder.withLayer(
        BackgroundLayer("background_fill")
            .withProperties(PropertyFactory.backgroundColor(android.graphics.Color.BLACK))
    )

    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    styleBuilder.withSource(RasterSource(SOURCE_BASE, TileSet("2.2.0", tileUrl), 256))
    styleBuilder.withLayer(RasterLayer(LAYER_BASE, SOURCE_BASE))

    map.setStyle(styleBuilder) { style ->
        
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                style.addSource(VectorSource(SOURCE_CULTIVO, TileSet("pbf", cultivoUrl)))
                style.addLayer(FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO).apply {
                    sourceLayer = SOURCE_LAYER_ID_CULTIVO
                    setProperties(
                        PropertyFactory.fillColor(Color.Yellow.copy(alpha = 0.3f).toArgb()),
                        PropertyFactory.fillAntialias(false)
                    )
                })
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                style.addSource(VectorSource(SOURCE_RECINTO, TileSet("pbf", recintoUrl)))

                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM

                // --- CAPA ANTI-RAYAS (FILLER) ---
                // Dibujamos una línea muy fina del color del relleno que tapa las costuras de los tiles
                style.addLayer(LineLayer("recinto_crack_filler", SOURCE_RECINTO).apply {
                    sourceLayer = SOURCE_LAYER_ID_RECINTO
                    setProperties(
                        PropertyFactory.lineColor(tintColor.copy(alpha = 0.15f).toArgb()),
                        PropertyFactory.lineWidth(0.8f)
                    )
                })

                // RELLENO ESTÁNDAR
                style.addLayer(FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO).apply {
                    sourceLayer = SOURCE_LAYER_ID_RECINTO
                    setProperties(
                        PropertyFactory.fillColor(tintColor.copy(alpha = 0.15f).toArgb()),
                        PropertyFactory.fillOutlineColor(android.graphics.Color.TRANSPARENT),
                        PropertyFactory.fillAntialias(false)
                    )
                })

                // BORDES DINÁMICOS (LineLayer es más robusto que fillOutline para evitar rejillas)
                style.addLayer(LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO).apply {
                    sourceLayer = SOURCE_LAYER_ID_RECINTO
                    setProperties(
                        PropertyFactory.lineColor(borderColor.toArgb()),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineWidth(
                            interpolate(linear(), zoom(), 
                                stop(12, 0.6f), 
                                stop(15, 1.5f), 
                                stop(18, 3.0f)
                            )
                        )
                    )
                })

                // RESALTADO (SELECCIÓN) - Corregido para evitar que aparezcan rayas al seleccionar
                val initialFilter = literal(false)

                // Resaltado de relleno
                style.addLayer(FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO).apply {
                    sourceLayer = SOURCE_LAYER_ID_RECINTO
                    setFilter(initialFilter)
                    setProperties(
                        PropertyFactory.fillColor(HighlightColor.copy(alpha = 0.4f).toArgb()),
                        PropertyFactory.fillAntialias(false)
                    )
                })

                // Resaltado de borde (LineLayer grueso para que destaque)
                style.addLayer(LineLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO).apply {
                    sourceLayer = SOURCE_LAYER_ID_RECINTO
                    setFilter(initialFilter)
                    setProperties(
                        PropertyFactory.lineColor(HighlightColor.toArgb()),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineWidth(
                            interpolate(linear(), zoom(),
                                stop(14, 2.0f),
                                stop(18, 5.0f)
                            )
                        )
                    )
                })
                
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
            val options = LocationComponentActivationOptions.builder(context, map.style!!).useDefaultLocationEngine(true).build()
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
    return android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
}
