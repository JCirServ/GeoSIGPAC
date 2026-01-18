package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// URLs y Nombres de Capas Internas (Source Layers) correctos para MVT
private const val SOURCE_RECINTO = "recinto-source"
private const val LAYER_RECINTO_LINE = "recinto-layer-line"
private const val SOURCE_LAYER_ID_RECINTO = "recinto" // El nombre interno del MVT

private const val SOURCE_CULTIVO = "cultivo-source"
private const val LAYER_CULTIVO_FILL = "cultivo-layer-fill"
private const val SOURCE_LAYER_ID_CULTIVO = "cultivo_declarado" // El nombre interno del MVT

// --- COORDENADAS POR DEFECTO (Comunidad Valenciana) ---
private val VALENCIA_LAT = 39.4699
private val VALENCIA_LNG = -0.3763
private val DEFAULT_ZOOM = 8.0
private val USER_TRACKING_ZOOM = 16.0 // Zoom alto para ver parcelas al localizar

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

            // Posición inicial por defecto: Comunidad Valenciana
            // Se hace antes de cargar el estilo para que el usuario vea algo familiar si falla el GPS
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(VALENCIA_LAT, VALENCIA_LNG))
                .zoom(DEFAULT_ZOOM)
                .build()

            // Cargar estilo inicial
            loadMapStyle(map, BaseMap.PNOA, showRecinto, showCultivo, context)
        }
    }

    // 2. Mover cámara (desde Web si se selecciona un proyecto)
    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(targetLat, targetLng))
                        .zoom(17.0) // Zoom muy cercano para ver la parcela
                        .tilt(0.0)
                        .build()
                ), 1500
            )
        }
    }

    // 3. Reaccionar a cambios de capas (Base u Overlays)
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
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
            // Botón Capas
            FloatingActionButton(
                onClick = { showLayerMenu = !showLayerMenu },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Capas y Configuración")
            }

            // Menú de Capas
            AnimatedVisibility(visible = showLayerMenu) {
                Card(
                    modifier = Modifier.width(240.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mapa Base", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        
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
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }
                        ) {
                            Checkbox(checked = showRecinto, onCheckedChange = { showRecinto = it })
                            Text("Recintos", fontSize = 14.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }
                        ) {
                            Checkbox(checked = showCultivo, onCheckedChange = { showCultivo = it })
                            Text("Cultivos Declarados", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Botón Mi Ubicación
        FloatingActionButton(
            onClick = {
                enableLocation(mapInstance, context, forceZoom = true)
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
 * Carga el estilo y las capas.
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
        styleBuilder.fromUri("https://demotiles.maplibre.org/style.json")
    } else {
        // PNOA Ortofoto (TMS HTTPS)
        val pnoaUrl = "https://tms-pnoa-ma.ign.es/1.0.0/pnoa-ma/{z}/{x}/{y}.jpeg"
        // TileSet constructor: (usage/version, vararg urls)
        val tileSet = TileSet("tiles", pnoaUrl)
        styleBuilder.withSource(RasterSource(SOURCE_PNOA, tileSet, 256))
        styleBuilder.withLayer(RasterLayer(LAYER_PNOA, SOURCE_PNOA))
    }

    map.setStyle(styleBuilder) { style ->
        
        // 2. CAPAS VECTORIALES (MVT)
        
        // --- CULTIVO DECLARADO (Relleno) ---
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val cultivoSource = VectorSource(SOURCE_CULTIVO, TileSet("pbf", cultivoUrl))
                style.addSource(cultivoSource)

                val fillLayer = FillLayer(LAYER_CULTIVO_FILL, SOURCE_CULTIVO)
                // IMPORTANTE: El sourceLayer debe coincidir con el nombre interno del MVT
                fillLayer.sourceLayer = SOURCE_LAYER_ID_CULTIVO 
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Yellow.toArgb()),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillOutlineColor(Color.Yellow.toArgb())
                )
                style.addLayer(fillLayer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- RECINTO (Líneas) ---
        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val recintoSource = VectorSource(SOURCE_RECINTO, TileSet("pbf", recintoUrl))
                style.addSource(recintoSource)

                val lineLayer = LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                // IMPORTANTE: El sourceLayer debe coincidir con el nombre interno del MVT
                lineLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                lineLayer.setProperties(
                    PropertyFactory.lineColor(Color.White.toArgb()),
                    PropertyFactory.lineWidth(1.5f)
                )
                style.addLayer(lineLayer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. ACTIVAR UBICACIÓN (Auto-Zoom si hay permisos)
        enableLocation(map, context, forceZoom = true)
    }
}

/**
 * Activa la ubicación y hace zoom a la posición del usuario.
 * @param forceZoom Si es true, fuerza el zoom al nivel 16 al activar el tracking.
 */
@SuppressLint("MissingPermission")
private fun enableLocation(map: MapLibreMap?, context: Context, forceZoom: Boolean = false) {
    if (map == null || map.style == null) return

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val locationComponent = map.locationComponent
        
        // Configuración para que el mapa haga zoom automático al usuario
        val options = LocationComponentActivationOptions.builder(context, map.style!!)
            .useDefaultLocationEngine(true)
            .build()
        
        locationComponent.activateLocationComponent(options)
        locationComponent.isLocationComponentEnabled = true
        
        // Modo TRACKING mueve la cámara al usuario
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
        
        // Si queremos forzar un nivel de zoom específico mientras seguimos al usuario
        if (forceZoom) {
            locationComponent.zoomWhileTracking(USER_TRACKING_ZOOM)
        }
    } else {
        // Fallback: Si no hay permiso, asegurar que estamos en Valencia (por si se llama manualmente)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(VALENCIA_LAT, VALENCIA_LNG), DEFAULT_ZOOM))
        Toast.makeText(context, "Sin permiso GPS: Mostrando Valencia", Toast.LENGTH_SHORT).show()
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