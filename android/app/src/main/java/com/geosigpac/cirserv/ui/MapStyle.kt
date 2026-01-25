
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
        
        // ----------------------------------------------------
        // ORDEN DE CAPAS BASE (Z-INDEX IMPLÍCITO DE ABAJO A ARRIBA)
        // ----------------------------------------------------

        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetCultivo = TileSet("pbf", cultivoUrl)
                tileSetCultivo.minZoom = 15f
                tileSetCultivo.maxZoom = 15f
                
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                // BASE CULTIVO (Amarillo)
                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.minZoom = 15f 
                fillLayer.setProperties(
                    PropertyFactory.fillColor(CultivoFillColor.toArgb()),
                    PropertyFactory.fillOpacity(CultivoFillOpacity),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(fillLayer)

                // Borde Cultivo (Amarillo Neón) - Este sí usa la técnica de "Thick Outline" segura
                val cropBorderColor = if (baseMap == BaseMap.PNOA) Color(0xFFFFEA00) else Color(0xFFF57F17)
                addThickOutline(
                    style = style,
                    sourceId = SOURCE_CULTIVO,
                    sourceLayer = SOURCE_LAYER_ID_CULTIVO,
                    baseLayerId = "cultivo-layer-line",
                    color = cropBorderColor.toArgb(),
                    minZoom = 15f,
                    offsetMult = 0.5f 
                )

            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 15f
                tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // BASE RECINTO (Cian Transparente)
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val tintLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                tintLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                tintLayer.minZoom = 15f
                tintLayer.setProperties(
                    PropertyFactory.fillColor(tintColor.toArgb()),
                    PropertyFactory.fillOpacity(0.1f),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(tintLayer)

                // Borde Recinto (Blanco/Gris)
                val borderColor = if (baseMap == BaseMap.PNOA) BorderColorPNOA else BorderColorOSM
                addThickOutline(
                    style = style,
                    sourceId = SOURCE_RECINTO,
                    sourceLayer = SOURCE_LAYER_ID_RECINTO,
                    baseLayerId = LAYER_RECINTO_LINE,
                    color = borderColor.toArgb(),
                    minZoom = 15f,
                    offsetMult = 1.0f 
                )
                
            } catch (e: Exception) { e.printStackTrace() }
        }

        // -----------------------------------------------------------------------
        // CAPAS DE RESALTADO (INTERACCIÓN) - AÑADIDAS AL FINAL (ARRIBA DE TODO)
        // -----------------------------------------------------------------------
        val initialFilter = Expression.literal(false)

        // 1. RESALTADO RECINTO (Naranja - Fondo)
        // Se añade PRIMERO.
        if (showRecinto) {
            val hRecintoFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
            hRecintoFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
            hRecintoFill.minZoom = 15f
            hRecintoFill.setFilter(initialFilter)
            hRecintoFill.setProperties(
                PropertyFactory.fillColor(HighlightColorRecinto.toArgb()),
                PropertyFactory.fillOpacity(0.5f), // Más visible ya que no hay borde grueso
                PropertyFactory.fillOutlineColor(HighlightColorRecinto.toArgb()), // Borde de 1px
                PropertyFactory.visibility(Property.VISIBLE)
            )
            style.addLayer(hRecintoFill)
        }

        // 2. RESALTADO CULTIVO (Cian - Frente)
        // Se añade DESPUÉS para que se pinte ENCIMA del recinto.
        if (showCultivo) {
            val hCultivoFill = FillLayer(LAYER_CULTIVO_HIGHLIGHT_FILL, SOURCE_CULTIVO)
            hCultivoFill.sourceLayer = SOURCE_LAYER_ID_CULTIVO
            hCultivoFill.minZoom = 15f
            hCultivoFill.setFilter(initialFilter)
            hCultivoFill.setProperties(
                PropertyFactory.fillColor(HighlightColorCultivo.toArgb()),
                PropertyFactory.fillOpacity(0.5f), // Visible sobre el naranja
                PropertyFactory.fillOutlineColor(HighlightColorCultivo.toArgb()), // Borde de 1px
                PropertyFactory.visibility(Property.VISIBLE)
            )
            style.addLayer(hCultivoFill) 
        }

        if (enableLocation(map, context, shouldCenterUser)) {
            onLocationEnabled()
        }
    }
}

private fun addThickOutline(
    style: Style,
    sourceId: String,
    sourceLayer: String,
    baseLayerId: String,
    color: Int,
    minZoom: Float,
    offsetMult: Float
) {
    // Capa Central (Siempre visible, línea fina base)
    val base = FillLayer(baseLayerId, sourceId)
    base.sourceLayer = sourceLayer
    base.minZoom = minZoom
    base.setProperties(
        PropertyFactory.fillColor(Color.Transparent.toArgb()),
        PropertyFactory.fillOutlineColor(color),
        PropertyFactory.fillAntialias(true)
    )
    style.addLayer(base)

    // Offsets para grosor (simulación de borde mediante capas desplazadas)
    val offsets = listOf(
        arrayOf(1f * offsetMult, 0f),
        arrayOf(0f, 1f * offsetMult),
        arrayOf(-1f * offsetMult, 0f),
        arrayOf(0f, -1f * offsetMult)
    )

    offsets.forEachIndexed { i, offset ->
        val layerName = "${baseLayerId}_thick_$i"
        if (style.getLayer(layerName) == null) {
            val layer = FillLayer(layerName, sourceId)
            layer.sourceLayer = sourceLayer
            layer.minZoom = minZoom

            // INTERPOLACIÓN DINÁMICA DE GROSOR:
            // Zoom < 14.5: Offset 0 (Solo se ve la línea fina base).
            // Zoom >= 16.0: Offset Completo (Se ve el borde grueso para detalle).
            // Entre 14.5 y 16.0: Transición suave.
            val dynamicTranslate = Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                Expression.stop(14.5f, arrayOf(0f, 0f)),
                Expression.stop(16.0f, offset)
            )

            layer.setProperties(
                PropertyFactory.fillColor(Color.Transparent.toArgb()),
                PropertyFactory.fillOutlineColor(color),
                PropertyFactory.fillAntialias(true),
                PropertyFactory.fillTranslate(dynamicTranslate)
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

fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
