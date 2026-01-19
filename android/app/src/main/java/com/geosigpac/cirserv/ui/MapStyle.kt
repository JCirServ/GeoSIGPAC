
package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

/**
 * Gestiona la carga de estilos y capas del mapa.
 * Se han añadido logs y correcciones en los constructores de TileSet para mayor estabilidad.
 */
fun loadMapStyle(
    map: MapLibreMap,
    baseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    context: Context,
    shouldCenterUser: Boolean,
    onLocationEnabled: () -> Unit
) {
    Log.d("MapStyle", "Iniciando carga de estilo: Base=$baseMap, Recinto=$showRecinto, Cultivo=$showCultivo")
    
    val styleBuilder = Style.Builder()

    // Configuración del Mapa Base (Raster)
    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    val tileSet = TileSet("2.1.0", tileUrl)
    tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
    
    val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
    styleBuilder.withSource(rasterSource)
    styleBuilder.withLayer(RasterLayer(LAYER_BASE, SOURCE_BASE))

    map.setStyle(styleBuilder) { style ->
        Log.d("MapStyle", "Estilo base cargado correctamente")
        
        // Capa de Cultivos (Vector MVT)
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                // El primer parámetro es la versión de TileJSON (p.ej. "2.1.0"), no el formato.
                val tileSetCultivo = TileSet("2.1.0", cultivoUrl).apply {
                    minZoom = 5f
                    maxZoom = 16f
                }
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.30f),
                    PropertyFactory.fillOutlineColor(Color.Yellow.copy(alpha = 0.5f).toArgb())
                )
                style.addLayer(fillLayer)
                Log.d("MapStyle", "Capa de Cultivo añadida")
            } catch (e: Exception) { 
                Log.e("MapStyle", "Error añadiendo capa Cultivo: ${e.message}")
            }
        }

        // Capa de Recintos (Vector MVT)
        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("2.1.0", recintoUrl).apply {
                    minZoom = 5f
                    maxZoom = 16f
                }

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // 1. Capa invisible para detección de features
                val detectionLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                detectionLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                detectionLayer.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOpacity(0.01f) 
                )
                style.addLayer(detectionLayer)

                // 2. Capas de Resaltado (Highlight)
                val initialFilter = Expression.literal(false)

                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.setFilter(initialFilter)
                highlightFill.setProperties(
                    PropertyFactory.fillColor(HighlightColor.toArgb()),
                    PropertyFactory.fillOpacity(0.45f)
                )
                style.addLayer(highlightFill)

                val highlightLine = LineLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO)
                highlightLine.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLine.setFilter(initialFilter)
                highlightLine.setProperties(
                    PropertyFactory.lineColor(HighlightColor.toArgb()),
                    PropertyFactory.lineWidth(3.5f)
                )
                style.addLayer(highlightLine)

                // 3. Capa de Bordes Generales
                val outlineLayer = FillLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                outlineLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                outlineLayer.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(RecintoLineColor.toArgb())
                )
                style.addLayer(outlineLayer)
                
                Log.d("MapStyle", "Capas de Recinto añadidas")
            } catch (e: Exception) { 
                Log.e("MapStyle", "Error añadiendo capas Recinto: ${e.message}")
            }
        }

        // Activación de Ubicación
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
        } catch (e: Exception) { 
            Log.e("MapStyle", "Error activando ubicación: ${e.message}")
        }
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
