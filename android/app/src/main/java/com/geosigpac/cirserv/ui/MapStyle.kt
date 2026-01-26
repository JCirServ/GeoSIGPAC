
package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineRequest
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
                tileSetCultivo.minZoom = 13f // Descarga desde nivel 13
                tileSetCultivo.maxZoom = 15f
                
                val cultivoSource = VectorSource(SOURCE_CULTIVO, tileSetCultivo)
                style.addSource(cultivoSource)

                // BASE CULTIVO (Amarillo)
                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO
                fillLayer.minZoom = 13f 
                fillLayer.setProperties(
                    PropertyFactory.fillColor(CultivoFillColor.toArgb()),
                    PropertyFactory.fillOpacity(CultivoFillOpacity),
                    PropertyFactory.fillOutlineColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillAntialias(false)
                )
                style.addLayer(fillLayer)

                // Borde Cultivo (Amarillo Neón)
                val cropBorderColor = if (baseMap == BaseMap.PNOA) Color(0xFFFFEA00) else Color(0xFFF57F17)
                addThickOutline(
                    style = style,
                    sourceId = SOURCE_CULTIVO,
                    sourceLayer = SOURCE_LAYER_ID_CULTIVO,
                    baseLayerId = "cultivo-layer-line",
                    color = cropBorderColor.toArgb(),
                    minZoom = 13f, // La línea base aparece en 13
                    thickZoom = 16f, // El grosor extra aparece en 16
                    offsetMult = 0.5f 
                )

            } catch (e: Exception) { e.printStackTrace() }
        }

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 13f // Descarga desde nivel 13
                tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                // BASE RECINTO (Cian Transparente)
                val tintColor = if (baseMap == BaseMap.PNOA) FillColorPNOA else FillColorOSM
                val tintLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                tintLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                tintLayer.minZoom = 13f
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
                    minZoom = 13f, // La línea base aparece en 13
                    thickZoom = 16f, // El grosor extra aparece en 16
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
            hRecintoFill.minZoom = 13f
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
            hCultivoFill.minZoom = 13f
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
    thickZoom: Float,
    offsetMult: Float
) {
    // Capa Central (Siempre visible desde minZoom, línea fina base 1px)
    // Esto asegura que de lejos (Zoom 13-15) veas algo limpio.
    val base = FillLayer(baseLayerId, sourceId)
    base.sourceLayer = sourceLayer
    base.minZoom = minZoom
    base.setProperties(
        PropertyFactory.fillColor(Color.Transparent.toArgb()),
        PropertyFactory.fillOutlineColor(color),
        PropertyFactory.fillAntialias(true)
    )
    style.addLayer(base)

    // Capas de grosor (Simulación de borde)
    // Solo visibles cuando te acercas (thickZoom = 16f)
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
            // IMPORTANTE: minZoom más alto para las capas de grosor.
            // No se descargan ni pintan hasta llegar al nivel de detalle.
            layer.minZoom = thickZoom 

            layer.setProperties(
                PropertyFactory.fillColor(Color.Transparent.toArgb()),
                PropertyFactory.fillOutlineColor(color),
                PropertyFactory.fillAntialias(true),
                PropertyFactory.fillTranslate(offset) // Offset fijo, sin interpolación
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
            
            // CONFIGURACIÓN DE ALTO RENDIMIENTO (Igual que la Cámara)
            // Intervalo base 1000ms, Rápido 500ms, Alta Precisión
            val request = LocationEngineRequest.Builder(1000)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(500) // Actualizaciones muy frecuentes
                .build()

            val options = LocationComponentActivationOptions.builder(context, map.style!!)
                .locationEngineRequest(request) // Aplicamos la config agresiva
                .useDefaultLocationEngine(true) // Usamos el motor interno (que usará Play Services)
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
