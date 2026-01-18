package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

// --- CONSTANTES DE CAPAS ---
private const val SOURCE_PNOA = "pnoa-source"
private const val LAYER_PNOA = "pnoa-layer"
private const val SOURCE_RECINTO = "recinto-source"
private const val LAYER_RECINTO_LINE = "recinto-layer-line"
private const val SOURCE_CULTIVO = "cultivo-source"
private const val LAYER_CULTIVO_FILL = "cultivo-layer-fill"
private const val LAYER_CULTIVO_LINE = "cultivo-layer-line"

enum class BaseMap(val title: String) {
    OSM("OpenStreetMap"),
    PNOA("Ortofoto PNOA")
}

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    targetLat: Double?,
    targetLng: Double?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // --- ESTADO ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

    // Inicializar MapLibre
    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // --- CICLO DE VIDA ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- EFECTOS DE CONTROL ---
    
    // 1. Cargar Estilo Base y Configuración Inicial
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true

            // Cargar estilo inicial (Empezamos con PNOA por defecto)
            loadMapStyle(map, BaseMap.PNOA, showRecinto, showCultivo, context)
        }
    }

    // 2. Mover cámara (desde Web)
    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(targetLat, targetLng))
                        .zoom(16.0)
                        .tilt(0.0)
                        .build()
                ), 1500
            )
        }
    }

    // 3. Reaccionar a cambios de capas (Base u Overlays)
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            // Recargamos el estilo completo para manejar correctamente el orden Z
            // En una app más compleja, añadiríamos/eliminaríamos capas dinámicamente
            // pero recargar el estilo garantiza el orden correcto (Base -> MVT).
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context)
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        
        // VISTA DEL MAPA
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // BOTONES FLOTANTES
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón Capas (Usamos Settings en lugar de Layers)
            FloatingActionButton(
                onClick = { showLayerMenu = !showLayerMenu },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Capas y Configuración")
            }

            // Menú de Capas (Visible/Oculto)
            AnimatedVisibility(visible = showLayerMenu) {
                Card(
                    modifier = Modifier.width(220.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mapa Base", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Selectores Base
                        BaseMap.values().forEach { base ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentBaseMap = base }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentBaseMap == base),
                                    onClick = { currentBaseMap = base },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(base.title, fontSize = 14.sp)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Capas SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        
                        // Checkbox Recinto
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }
                        ) {
                            Checkbox(checked = showRecinto, onCheckedChange = { showRecinto = it })
                            Text("Recintos (Líneas)", fontSize = 14.sp)
                        }

                        // Checkbox Cultivo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }
                        ) {
                            Checkbox(checked = showCultivo, onCheckedChange = { showCultivo = it })
                            Text("Cultivos (Relleno)", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Botón Mi Ubicación (Usamos LocationOn en lugar de MyLocation)
        FloatingActionButton(
            onClick = {
                enableLocation(mapInstance, context)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Mi Ubicación")
        }
    }
}

/**
 * Función auxiliar para construir y cargar el estilo del mapa.
 */
private fun loadMapStyle(
    map: MapLibreMap,
    baseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    context: Context
) {
    val styleBuilder = Style.Builder()

    // 1. CONFIGURAR MAPA BASE
    if (baseMap == BaseMap.OSM) {
        // Estilo vectorial básico OpenSource (OSM Bright)
        styleBuilder.fromUri("https://demotiles.maplibre.org/style.json")
    } else {
        // PNOA (Raster XYZ/TMS)
        // Construimos un estilo "vacío" y añadimos la fuente raster manualmente
        // URL Template del PNOA (España)
        val pnoaUrl = "https://tms-pnoa-ma.ign.es/1.0.0/pnoa-ma/{z}/{x}/{y}.jpeg"
        val tileSet = TileSet("2.1.0", pnoaUrl)
        // La propiedad 'tileSize' no existe en TileSet, se pasa en el constructor de RasterSource
        
        styleBuilder.withSource(RasterSource(SOURCE_PNOA, tileSet, 256))
        styleBuilder.withLayer(RasterLayer(LAYER_PNOA, SOURCE_PNOA))
    }

    map.setStyle(styleBuilder) { style ->
        
        // 2. AÑADIR CAPAS VECTORIALES (MVT) ENCIMA DEL BASE
        // Nota: Los estilos vectoriales se añaden una vez cargado el base para asegurar que queden encima.

        // --- CULTIVO DECLARADO ---
        if (showCultivo) {
            try {
                // URL: https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val cultivoSource = VectorSource(SOURCE_CULTIVO, TileSet("pbf", cultivoUrl))
                
                style.addSource(cultivoSource)

                // Relleno semi-transparente amarillo/naranja
                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                fillLayer.sourceLayer = "default" // Asumimos layer 'default' o investigar nombre real del vector
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.3f),
                    PropertyFactory.fillOutlineColor(Color.Yellow.toArgb()) // Deprecated pero útil para debug rápido
                )
                style.addLayer(fillLayer)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- RECINTO ---
        if (showRecinto) {
            try {
                // URL: https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val recintoSource = VectorSource(SOURCE_RECINTO, TileSet("pbf", recintoUrl))
                
                style.addSource(recintoSource)

                // Líneas blancas gruesas para los límites
                val lineLayer = LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                lineLayer.sourceLayer = "default" // Asumimos layer 'default'
                lineLayer.setProperties(
                    PropertyFactory.lineColor(Color.White.toArgb()),
                    PropertyFactory.lineWidth(2f)
                )
                style.addLayer(lineLayer)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. ACTIVAR UBICACIÓN AL CARGAR
        enableLocation(map, context)
    }
}

/**
 * Helper para activar la ubicación del usuario
 */
@SuppressLint("MissingPermission")
private fun enableLocation(map: MapLibreMap?, context: Context) {
    if (map == null || map.style == null) return

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val locationComponent = map.locationComponent
        val options = LocationComponentActivationOptions.builder(context, map.style!!)
            .build()
        
        locationComponent.activateLocationComponent(options)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
    }
}

// Extensión para convertir Color Compose a ARGB int
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}