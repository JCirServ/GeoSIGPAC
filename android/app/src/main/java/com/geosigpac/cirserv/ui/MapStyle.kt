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
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

// Constantes sugeridas (asegúrate de que coincidan con tus definiciones)
const val SOURCE_BASE = "source_base"
const val LAYER_BASE = "layer_base"
const val SOURCE_CULTIVO = "source_cultivo"
const val LAYER_CULTIVO_FILL = "layer_cultivo_fill"
const val SOURCE_LAYER_ID_CULTIVO = "cultivo_declarado" // Ajustar según tu MVT
const val SOURCE_RECINTO = "source_recinto"
const val LAYER_RECINTO_FILL = "layer_recinto_fill"
const val LAYER_RECINTO_LINE = "layer_recinto_line"
const val SOURCE_LAYER_ID_RECINTO = "recinto" // Ajustar según tu MVT
const val LAYER_RECINTO_HIGHLIGHT_FILL = "layer_highlight_fill"
const val LAYER_RECINTO_HIGHLIGHT_LINE = "layer_highlight_line"
const val USER_TRACKING_ZOOM = 15.0

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

    // 1. CAPA DE FONDO: Soluciona las líneas blancas que atraviesan el mapa base
    val backgroundLayer = BackgroundLayer("background_fill")
    backgroundLayer.setProperties(PropertyFactory.backgroundColor(android.graphics.Color.BLACK))
    styleBuilder.withLayer(backgroundLayer)

    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    val tileSet = TileSet("2.2.0", tileUrl)
    tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
    
    val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
    styleBuilder.withSource(rasterSource)

    // 2. CAPA RÁSTER (Satélite/OSM)
    val baseLayer = RasterLayer(LAYER_BASE, SOURCE_BASE)
    styleBuilder.withLayer(baseLayer)

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
                    // CORRECCIÓN: Alpha integrado en el color para evitar rayas en las costuras
                    PropertyFactory.fillColor(Color.Yellow.copy(alpha = 0.35f).toArgb()),
                    PropertyFactory.fillAntialias(false)
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

                // CAPA 1: RELLENO (TINT) - Elimina las rayas naranjas de tus capturas
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val tintLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                tintLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                tintLayer.setProperties(
                    // CLAVE: No usar fillOpacity. El alpha va dentro del fillColor.
                    PropertyFactory.fillColor(tintColor.copy(alpha = 0.15f).toArgb()),
                    // Forzamos que no haya un borde interno dibujado por el motor de relleno
                    PropertyFactory.fillOutlineColor(android.graphics.Color.TRANSPARENT),
                    // Desactivamos el suavizado de bordes para que los tiles encajen sin "gaps"
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(tintLayer)

                // CAPA 2: BORDE (OUTLINE) - Mejora la continuidad visual
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM
                val borderLayer = LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                borderLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                borderLayer.setProperties(
                    PropertyFactory.lineColor(borderColor.toArgb()),
                    PropertyFactory.lineWidth(1.8f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                )
                style.addLayer(borderLayer)

                // CAPAS DE RESALTADO (SELECCIÓN)
                val initialFilter = Expression.literal(false)

                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.setFilter(initialFilter)
                highlightFill.setProperties(
                    PropertyFactory.fillColor(HighlightColor.copy(alpha = 0.5f).toArgb()),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(highlightFill)

                val highlightLine = LineLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO)
                highlightLine.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLine.setFilter(initialFilter)
                highlightLine.setProperties(
                    PropertyFactory.lineColor(HighlightColor.toArgb()),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
                style.addLayer(highlightLine)
                
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

// Extension function para manejar colores de Compose
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
