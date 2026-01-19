
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

private const val TAG = "GeoSIGPAC_LOG_Style"

fun loadMapStyle(
    map: MapLibreMap,
    baseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    context: Context,
    shouldCenterUser: Boolean,
    onLocationEnabled: () -> Unit
) {
    Log.d(TAG, "Iniciando configuración de estilo: Base=$baseMap, Recinto=$showRecinto, Cultivo=$showCultivo")
    val styleBuilder = Style.Builder()

    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    Log.d(TAG, "Creando RasterSource para mapa base: $tileUrl")
    val tileSet = TileSet("2.2.0", tileUrl)
    tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
    
    val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
    styleBuilder.withSource(rasterSource)
    styleBuilder.withLayer(RasterLayer(LAYER_BASE, SOURCE_BASE))

    map.setStyle(styleBuilder) { style ->
        Log.i(TAG, "Estilo base aplicado. Añadiendo capas vectoriales...")
        
        if (showCultivo) {
            try {
                Log.d(TAG, "Añadiendo fuente de CULTIVOS (Vector)")
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
                    PropertyFactory.fillOutlineColor(Color.Yellow.toArgb())
                )
                style.addLayer(fillLayer)
                Log.d(TAG, "Capa de cultivo añadida con éxito")
            } catch (e: Exception) { 
                Log.e(TAG, "Error añadiendo fuente de CULTIVOS: ${e.message}", e)
            }
        }

        if (showRecinto) {
            try {
                Log.d(TAG, "Añadiendo fuente de RECINTOS (Vector)")
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 5f; tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                Log.d(TAG, "Añadiendo capas de dibujo para recintos")
                val detectionLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                detectionLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                detectionLayer.setProperties(PropertyFactory.fillOpacity(0.01f))
                style.addLayer(detectionLayer)

                val highlightFill = FillLayer(LAYER_RECINTO_HIGHLIGHT_FILL, SOURCE_RECINTO)
                highlightFill.sourceLayer = SOURCE_LAYER_ID_RECINTO
                highlightFill.setFilter(Expression.literal(false))
                highlightFill.setProperties(PropertyFactory.fillColor(HighlightColor.toArgb()), PropertyFactory.fillOpacity(0.4f))
                style.addLayer(highlightFill)

                val outlineLayer = FillLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                outlineLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                outlineLayer.setProperties(PropertyFactory.fillOutlineColor(RecintoLineColor.toArgb()))
                style.addLayer(outlineLayer)
                Log.d(TAG, "Capas de recinto añadidas con éxito")
                
            } catch (e: Exception) { 
                Log.e(TAG, "Error añadiendo fuente de RECINTOS: ${e.message}", e)
            }
        }

        if (enableLocation(map, context, shouldCenterUser)) {
            Log.i(TAG, "Componente de localización activado")
            onLocationEnabled()
        }
    }
}

@SuppressLint("MissingPermission")
fun enableLocation(map: MapLibreMap?, context: Context, shouldCenter: Boolean): Boolean {
    if (map == null || map.style == null) {
        Log.w(TAG, "No se puede habilitar localización: mapa o estilo nulo")
        return false
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        try {
            val locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(context, map.style!!)
                .useDefaultLocationEngine(true)
                .build()
            
            locationComponent.activateLocationComponent(options)
            locationComponent.isLocationComponentEnabled = true
            
            if (shouldCenter) {
                locationComponent.cameraMode = CameraMode.TRACKING
            }
            return true
        } catch (e: Exception) { 
            Log.e(TAG, "Fallo al activar LocationComponent: ${e.message}", e)
        }
    } else {
        Log.w(TAG, "Permisos de localización no concedidos")
    }
    return false
}

fun Color.toArgb(): Int {
    return android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
}
