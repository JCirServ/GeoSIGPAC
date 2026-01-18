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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
private const val SOURCE_BASE = "base-source"
private const val LAYER_BASE = "base-layer"

// Capas SIGPAC (MVT)
private const val SOURCE_RECINTO = "recinto-source"
private const val LAYER_RECINTO_LINE = "recinto-layer-line"
private const val SOURCE_LAYER_ID_RECINTO = "recinto"

private const val SOURCE_CULTIVO = "cultivo-source"
private const val LAYER_CULTIVO_FILL = "cultivo-layer-fill"
private const val SOURCE_LAYER_ID_CULTIVO = "cultivo_declarado"

// --- COORDENADAS POR DEFECTO (Comunidad Valenciana) ---
private val VALENCIA_LAT = 39.4699
private val VALENCIA_LNG = -0.3763
private val DEFAULT_ZOOM = 16.0
private val USER_TRACKING_ZOOM = 16.0

enum class BaseMap(val title: String) {
    OSM("OpenStreetMap"),
    PNOA("Ortofoto PNOA")
}

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    targetLat: Double?,
    targetLng: Double?,
    onNavigateToProjects: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // --- ESTADO ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

    // Controla si ya hemos centrado la cámara en el usuario al inicio
    var initialLocationSet by remember { mutableStateOf(false) }

    // --- ICONOS MANUALES (Para evitar dependencias extra) ---
    val CameraAltIcon = remember {
        ImageVector.Builder(
            name = "CameraAlt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.0f, 2.0f)
                lineTo(7.17f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                verticalLineTo(18.0f)
                curveTo(2.0f, 19.1f, 2.9f, 20.0f, 4.0f, 20.0f)
                horizontalLineTo(20.0f)
                curveTo(21.1f, 20.0f, 22.0f, 19.1f, 22.0f, 18.0f)
                verticalLineTo(6.0f)
                curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
                lineTo(16.83f, 4.0f)
                lineTo(15.0f, 2.0f)
                lineTo(9.0f, 2.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveTo(9.24f, 17.0f, 7.0f, 14.76f, 7.0f, 12.0f)
                curveTo(7.0f, 9.24f, 9.24f, 7.0f, 12.0f, 7.0f)
                curveTo(14.76f, 7.0f, 17.0f, 9.24f, 17.0f, 12.0f)
                curveTo(17.0f, 14.76f, 14.76f, 17.0f, 12.0f, 17.0f)
                close()
            }
        }.build()
    }

    val MyLocationIcon = remember {
        ImageVector.Builder(
            name = "MyLocation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 8.0f)
                curveTo(9.79f, 8.0f, 8.0f, 9.79f, 8.0f, 12.0f)
                curveTo(8.0f, 14.21f, 9.79f, 16.0f, 12.0f, 16.0f)
                curveTo(14.21f, 16.0f, 16.0f, 14.21f, 16.0f, 12.0f)
                curveTo(16.0f, 9.79f, 14.21f, 8.0f, 12.0f, 8.0f)
                close()
                moveTo(20.94f, 11.0f)
                curveTo(20.48f, 6.83f, 17.17f, 3.52f, 13.0f, 3.06f)
                verticalLineTo(1.0f)
                horizontalLineTo(11.0f)
                verticalLineTo(3.06f)
                curveTo(6.83f, 3.52f, 3.52f, 6.83f, 3.06f, 11.0f)
                horizontalLineTo(1.0f)
                verticalLineTo(13.0f)
                horizontalLineTo(3.06f)
                curveTo(3.52f, 17.17f, 6.83f, 20.48f, 11.0f, 20.94f)
                verticalLineTo(23.0f)
                horizontalLineTo(13.0f)
                verticalLineTo(20.94f)
                curveTo(17.17f, 20.48f, 20.48f, 17.17f, 20.94f, 13.0f)
                horizontalLineTo(23.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(20.94f)
                close()
                moveTo(12.0f, 19.0f)
                curveTo(8.13f, 19.0f, 5.0f, 15.87f, 5.0f, 12.0f)
                curveTo(5.0f, 8.13f, 8.13f, 5.0f, 12.0f, 5.0f)
                curveTo(15.87f, 5.0f, 19.0f, 8.13f, 19.0f, 12.0f)
                curveTo(19.0f, 15.87f, 15.87f, 19.0f, 12.0f, 19.0f)
                close()
            }
        }.build()
    }

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
    
    // 1. Inicialización
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true

            // Posición inicial por defecto (Valencia)
            // Solo si no se ha establecido ubicación previa
            if (!initialLocationSet) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(VALENCIA_LAT, VALENCIA_LNG))
                    .zoom(DEFAULT_ZOOM)
                    .build()
            }

            // Cargar estilo y configurar ubicación
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
                // Callback: Se ha activado la ubicación correctamente
                initialLocationSet = true
            }
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

    // 3. Reaccionar a cambios de capas
    // IMPORTANTE: Al cambiar capas, pasamos false a shouldCenterUser para NO mover la cámara
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) {
                // No action needed on layer switch location update
            }
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // --- COLUMNA DE CONTROLES (TOP-RIGHT) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Botón Configuración (Capas)
            SmallFloatingActionButton(
                onClick = { showLayerMenu = !showLayerMenu },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Capas")
            }

            // Menú Desplegable de Capas
            AnimatedVisibility(visible = showLayerMenu) {
                Card(
                    modifier = Modifier.width(200.dp),
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
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(base.title, fontSize = 13.sp)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Capas SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }
                        ) {
                            Checkbox(
                                checked = showRecinto, 
                                onCheckedChange = { showRecinto = it },
                                modifier = Modifier.size(30.dp).padding(4.dp)
                            )
                            Text("Recintos", fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }
                        ) {
                            Checkbox(
                                checked = showCultivo, 
                                onCheckedChange = { showCultivo = it },
                                modifier = Modifier.size(30.dp).padding(4.dp)
                            )
                            Text("Cultivos", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            // 2. Botón Proyectos (Volver a la Web)
            SmallFloatingActionButton(
                onClick = onNavigateToProjects,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E), // Primary green
                shape = CircleShape
            ) {
                Icon(Icons.Default.List, contentDescription = "Proyectos")
            }
            
            // 3. Botón Cámara (Acceso Rápido)
            SmallFloatingActionButton(
                onClick = onOpenCamera,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E),
                shape = CircleShape
            ) {
                // USAMOS EL ICONO MANUALMENTE DEFINIDO
                Icon(CameraAltIcon, contentDescription = "Cámara")
            }
        }

        // --- BOTÓN CENTRAR UBICACIÓN (BOTTOM-RIGHT) ---
        FloatingActionButton(
            onClick = {
                // Al pulsar el botón explícitamente, SÍ queremos centrar (true)
                enableLocation(mapInstance, context, shouldCenter = true)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            // USAMOS EL ICONO MANUALMENTE DEFINIDO
            Icon(MyLocationIcon, contentDescription = "Centrar Ubicación")
        }
    }
}

/**
 * Reconstruye el estilo del mapa.
 */
private fun loadMapStyle(
    map: MapLibreMap,
    baseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    context: Context,
    shouldCenterUser: Boolean,
    onLocationEnabled: () -> Unit
) {
    val styleBuilder = Style.Builder()

    // 1. MAPA BASE (RASTER)
    val tileUrl = if (baseMap == BaseMap.OSM) {
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    } else {
        // PNOA WMTS (IGN España) - Compatible Google Maps
        // Usamos la capa "OI.OrthoimageCoverage" (Ortoimagen) con TileMatrixSet GoogleMapsCompatible
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    val tileSet = TileSet("2.2.0", tileUrl)
    if (baseMap == BaseMap.OSM) {
        tileSet.attribution = "© OpenStreetMap contributors"
    } else {
        tileSet.attribution = "© IGN PNOA"
    }
    
    val rasterSource = RasterSource(SOURCE_BASE, tileSet, 256)
    styleBuilder.withSource(rasterSource)
    styleBuilder.withLayer(RasterLayer(LAYER_BASE, SOURCE_BASE))

    map.setStyle(styleBuilder) { style ->
        
        // 2. CAPAS VECTORIALES (MVT)
        
        // --- CULTIVO (Relleno) ---
        if (showCultivo) {
            try {
                val cultivoUrl = "https://sigpac-hubcloud.es/mvt/cultivo_declarado@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetCultivo = TileSet("pbf", cultivoUrl)
                
                // CRUCIAL: Permitir Overzoom. 
                // El servidor da tiles hasta el nivel 15. Si el usuario hace zoom a 16, 17, 18,
                // usamos los tiles del nivel 15 estirados.
                tileSetCultivo.minZoom = 5f
                tileSetCultivo.maxZoom = 15f
                
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- RECINTO (Líneas) ---
        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                
                // CRUCIAL: Permitir Overzoom también aquí.
                tileSetRecinto.minZoom = 5f
                tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                val lineLayer = LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
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

        // 3. Restaurar configuración de ubicación
        if (enableLocation(map, context, shouldCenterUser)) {
            onLocationEnabled()
        }
    }
}

/**
 * Activa o restaura la ubicación.
 * @return true si se activó con éxito (hay permisos).
 */
@SuppressLint("MissingPermission")
private fun enableLocation(map: MapLibreMap?, context: Context, shouldCenter: Boolean): Boolean {
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
                // Solo movemos la cámara si se solicita explícitamente (inicio o botón click)
                locationComponent.cameraMode = CameraMode.TRACKING
                locationComponent.zoomWhileTracking(USER_TRACKING_ZOOM)
            } else {
                // Si solo estamos refrescando capas, NO movemos la cámara
                // Usamos NONE para ver el punto azul pero mantener el control manual
                locationComponent.cameraMode = CameraMode.NONE
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
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