
package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
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
                // AJUSTE SOLICITADO: Zoom 14 y 15
                tileSetCultivo.minZoom = 14f
                tileSetCultivo.maxZoom = 15f
                
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                // CAPA 1: RELLENO CULTIVO
                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.minZoom = 14f // Evitar renderizado prematuro
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()), // Limpiamos outline por defecto
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(fillLayer)

                // CAPA 2: BORDE GRUESO (Thick Outline) para separación de polígonos
                // Usamos blanco en satélite para contraste fuerte, gris en mapa base claro.
                val cropBorderColor = if (baseMap == BaseMap.PNOA) Color.White else Color.DarkGray
                
                addThickOutline(
                    style = style,
                    sourceId = SOURCE_CULTIVO,
                    sourceLayer = SOURCE_LAYER_ID_CULTIVO,
                    baseLayerId = "cultivo-layer-line", // ID único para borde cultivo
                    color = cropBorderColor.toArgb(),
                    minZoom = 14f
                )

            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                // AJUSTE SOLICITADO: Zoom 14 y 15
                tileSetRecinto.minZoom = 14f
                tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // CAPA 1: RELLENO (TINT)
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val tintLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                tintLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                tintLayer.minZoom = 14f // Evitar renderizado prematuro
                tintLayer.setProperties(
                    PropertyFactory.fillColor(tintColor.toArgb()),
                    PropertyFactory.fillOpacity(0.15f),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(tintLayer)

                // CAPA 2: BORDE (OUTLINE) - Solución Robusta "Thick Outline"
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM
                
                addThickOutline(
                    style = style,
                    sourceId = SOURCE_RECINTO,
                    sourceLayer = SOURCE_LAYER_ID_RECINTO,
                    baseLayerId = LAYER_RECINTO_LINE,
                    color = borderColor.toArgb(),
                    minZoom = 14f // Pasamos el zoom mínimo
                )

                // CAPAS DE RESALTADO (SELECCIÓN)
                val initialFilter = Expression.literal(false)

                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.minZoom = 14f
                highlightFill.setFilter(initialFilter)
                highlightFill.setProperties(
                    PropertyFactory.fillColor(HighlightColor.toArgb()),
                    PropertyFactory.fillOpacity(0.5f), 
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(highlightFill)

                // Resaltado de Borde
                val highlightLine = FillLayer(LAYER_RECINTO_HIGHLIGHT_LINE, SOURCE_RECINTO)
                highlightLine.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightLine.minZoom = 14f
                highlightLine.setFilter(initialFilter)
                highlightLine.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOutlineColor(HighlightColor.toArgb()),
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.fillAntialias(true)
                )
                style.addLayer(highlightLine)
                
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (enableLocation(map, context, shouldCenterUser)) {
            onLocationEnabled()
        }
    }
}

/**
 * Función Helper para simular bordes gruesos usando múltiples capas FillLayer con offsets.
 * Esto evita el uso de LineLayer que genera artifacts de malla en vectores de polígonos (MVT).
 */
private fun addThickOutline(
    style: Style,
    sourceId: String,
    sourceLayer: String,
    baseLayerId: String,
    color: Int,
    minZoom: Float
) {
    // 1. Capa Central (Referencia)
    val base = FillLayer(baseLayerId, sourceId)
    base.sourceLayer = sourceLayer
    base.minZoom = minZoom
    base.setProperties(
        PropertyFactory.fillColor(Color.Transparent.toArgb()),
        PropertyFactory.fillOutlineColor(color),
        PropertyFactory.fillAntialias(true)
    )
    style.addLayer(base)

    // 2. Capas de Offset para simular grosor (~2-3px visuales)
    // Se dibujan DEBAJO de la línea principal para mantener nitidez central
    val offsets = listOf(
        arrayOf(1f, 0f),
        arrayOf(0f, 1f),
        arrayOf(-1f, 0f),
        arrayOf(0f, -1f)
    )

    offsets.forEachIndexed { i, offset ->
        val layerName = "${baseLayerId}_thick_$i"
        // Aseguramos no duplicar si por alguna razón se llama dos veces
        if (style.getLayer(layerName) == null) {
            val layer = FillLayer(layerName, sourceId)
            layer.sourceLayer = sourceLayer
            layer.minZoom = minZoom
            layer.setProperties(
                PropertyFactory.fillColor(Color.Transparent.toArgb()),
                PropertyFactory.fillOutlineColor(color),
                PropertyFactory.fillAntialias(true),
                PropertyFactory.fillTranslate(offset)
            )
            style.addLayerBelow(layer, baseLayerId)
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
