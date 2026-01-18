package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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
    val scope = rememberCoroutineScope()
    
    // --- ESTADO ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

    // Controla si ya hemos centrado la cámara en el usuario al inicio
    var initialLocationSet by remember { mutableStateOf(false) }

    // Estado de datos SIGPAC (Panel Inferior)
    var sigpacData by remember { mutableStateOf<Map<String, String>?>(null) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

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
    
    // 1. Inicialización y Listeners
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false // Evitar inclinación accidental para mantener el puntero preciso

            // Posición inicial por defecto
            if (!initialLocationSet) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(VALENCIA_LAT, VALENCIA_LNG))
                    .zoom(DEFAULT_ZOOM)
                    .build()
            }

            // LISTENER: Al empezar a mover, ocultamos datos antiguos o loading
            map.addOnCameraMoveStartedListener {
                apiJob?.cancel()
                isLoadingData = false
                // Opcional: Ocultar panel al mover, o dejarlo visible. 
                // Para "emerger", mejor ocultarlo si se mueve lejos, pero aquí lo ocultamos para refrescar.
                sigpacData = null 
            }

            // LISTENER: Al detenerse, buscamos qué hay debajo de la cruz
            map.addOnCameraIdleListener {
                val center = map.cameraPosition.target
                // Solo buscamos si el zoom es suficiente para ver parcelas y si center no es nulo
                if (center != null && map.cameraPosition.zoom > 13) {
                    
                    // --- LOGGING DETALLADO MVT ---
                    // Obtenemos las coordenadas de pantalla del centro
                    val screenPoint = map.projection.toScreenLocation(center)
                    // Consultamos qué features hay renderizadas en ese píxel
                    val features = map.queryRenderedFeatures(screenPoint)
                    
                    Log.i("MVT_INSPECTOR", "===========================================================")
                    Log.i("MVT_INSPECTOR", "DATOS MVT EN CENTRO: Lat ${center.latitude}, Lng ${center.longitude}")
                    if (features.isEmpty()) {
                        Log.i("MVT_INSPECTOR", "No se encontraron features vectoriales aquí (o no se han cargado aún).")
                    } else {
                        features.forEachIndexed { index, feature ->
                            // En Android SDK, Feature es GeoJSON puro y no contiene layerId ni sourceId directamente
                            Log.i("MVT_INSPECTOR", "Feature #$index") 
                            val props = feature.properties()
                            if (props != null) {
                                props.entrySet().forEach { entry ->
                                    Log.d("MVT_INSPECTOR", "   > [${entry.key}] = ${entry.value}")
                                }
                            } else {
                                Log.d("MVT_INSPECTOR", "   > Sin propiedades")
                            }
                        }
                    }
                    Log.i("MVT_INSPECTOR", "===========================================================")
                    // -----------------------------

                    isLoadingData = true
                    apiJob?.cancel()
                    apiJob = scope.launch {
                        // Pequeño delay para asegurar que el usuario ha parado de verdad
                        delay(300) 
                        val data = fetchFullSigpacInfo(center.latitude, center.longitude)
                        sigpacData = data
                        isLoadingData = false
                    }
                } else {
                    sigpacData = null
                }
            }

            // Cargar estilo
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
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
                        .zoom(18.0) // Zoom alto para ver detalle
                        .tilt(0.0)
                        .build()
                ), 1500
            )
        }
    }

    // 3. Reaccionar a cambios de capas
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) {
                // No action needed
            }
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // --- CRUZ CENTRAL (Retícula) ---
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Sombra ligera para contraste
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(38.dp)
            )
            // Cruz blanca principal
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Puntero",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // --- COLUMNA DE CONTROLES (TOP-RIGHT) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón Configuración (Capas)
            SmallFloatingActionButton(
                onClick = { showLayerMenu = !showLayerMenu },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { Icon(Icons.Default.Settings, contentDescription = "Capas") }

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
                                modifier = Modifier.fillMaxWidth().clickable { currentBaseMap = base }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (currentBaseMap == base), onClick = { currentBaseMap = base }, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(base.title, fontSize = 13.sp)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Capas SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }) {
                            Checkbox(checked = showRecinto, onCheckedChange = { showRecinto = it }, modifier = Modifier.size(30.dp).padding(4.dp))
                            Text("Recintos", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }) {
                            Checkbox(checked = showCultivo, onCheckedChange = { showCultivo = it }, modifier = Modifier.size(30.dp).padding(4.dp))
                            Text("Cultivos", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            // Botón Proyectos
            SmallFloatingActionButton(
                onClick = onNavigateToProjects,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E),
                shape = CircleShape
            ) { Icon(Icons.Default.List, contentDescription = "Proyectos") }
            
            // Botón Cámara
            SmallFloatingActionButton(
                onClick = onOpenCamera,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E),
                shape = CircleShape
            ) { Icon(Icons.Default.CameraAlt, contentDescription = "Cámara") }

            // Botón Ubicación
            SmallFloatingActionButton(
                onClick = { enableLocation(mapInstance, context, shouldCenter = true) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Centrar Ubicación") }
        }

        // --- LOADING INDICATOR ---
        if (isLoadingData) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
            }
        }

        // --- PANEL DE DATOS INFERIOR (BOTTOM SHEET) ---
        AnimatedVisibility(
            visible = sigpacData != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            sigpacData?.let { data ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .heightIn(max = 350.dp), // Altura máxima para scroll
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Cabecera: Referencia SIGPAC
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text("REF. SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    text = "${data["provincia"]}/${data["municipio"]}/${data["agregado"]}/${data["zona"]}/${data["poligono"]}/${data["parcela"]}/${data["recinto"]}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { sigpacData = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Cerrar")
                            }
                        }
                        
                        Divider(Modifier.padding(vertical = 8.dp))

                        // Datos en Grid/Flow
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            
                            // Bloque 1: Uso y Superficie
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                AttributeItem("Uso SIGPAC", data["uso_sigpac"], Modifier.weight(1f))
                                AttributeItem("Superficie", "${data["superficie"]} m²", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            // Bloque 2: Pendiente y Altitud
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                AttributeItem("Pendiente Media", "${data["pendiente_media"]}%", Modifier.weight(1f))
                                AttributeItem("Altitud", "${data["altitud"]} m", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))

                            // Bloque 3: Administrativo
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                AttributeItem("Región", data["region"], Modifier.weight(1f))
                                AttributeItem("Coef. Regadío", "${data["coef_regadio"]}%", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))

                             // Bloque 4: Otros
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                AttributeItem("Subvencionabilidad", "${data["subvencionabilidad"]}%", Modifier.weight(1f))
                                AttributeItem("Incidencias", data["incidencias"]?.takeIf { it.isNotEmpty() } ?: "Ninguna", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttributeItem(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
        Text(value ?: "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// --- FUNCIÓN DE API ---
private suspend fun fetchFullSigpacInfo(lat: Double, lng: Double): Map<String, String>? = withContext(Dispatchers.IO) {
    try {
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
        
        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close()
            connection.disconnect()
            
            val jsonResponse = response.toString().trim()
            var targetJson: JSONObject? = null
            
            // La API puede devolver array o objeto
            if (jsonResponse.startsWith("[")) {
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) targetJson = jsonArray.getJSONObject(0)
            } else if (jsonResponse.startsWith("{")) {
                targetJson = JSONObject(jsonResponse)
            }

            if (targetJson != null) {
                // Función auxiliar para buscar propiedades anidadas en GeoJSON
                fun getProp(key: String): String {
                    // 1. Directo en raíz
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    
                    // 2. En "properties"
                    val props = targetJson!!.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optString(key)
                    
                    // 3. En "features[0].properties"
                    val features = targetJson!!.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        val fProps = features.getJSONObject(0).optJSONObject("properties")
                        if (fProps != null && fProps.has(key)) return fProps.optString(key)
                    }
                    return ""
                }

                val prov = getProp("provincia")
                val mun = getProp("municipio")
                val pol = getProp("poligono")
                
                // Si no hay datos básicos, no es un recinto válido
                if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return@withContext null

                return@withContext mapOf(
                    "provincia" to prov,
                    "municipio" to mun,
                    "agregado" to getProp("agregado"),
                    "zona" to getProp("zona"),
                    "poligono" to pol,
                    "parcela" to getProp("parcela"),
                    "recinto" to getProp("recinto"),
                    "superficie" to getProp("superficie"), // m2
                    "pendiente_media" to getProp("pendiente_media"),
                    "altitud" to getProp("altitud_media"), // Mapeo altitud_media -> altitud
                    "uso_sigpac" to getProp("uso_sigpac"),
                    "subvencionabilidad" to getProp("coef_admisibilidad_pastos"), // CAP -> subvencionabilidad
                    "coef_regadio" to getProp("coef_regadio"),
                    "incidencias" to getProp("incidencias").replace("[", "").replace("]", "").replace("\"", ""), // Limpieza array
                    "region" to getProp("region")
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
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
        // PNOA WMTS (IGN España)
        "https://www.ign.es/wmts/pnoa-ma?request=GetTile&service=WMTS&version=1.0.0&layer=OI.OrthoimageCoverage&style=default&format=image/jpeg&tilematrixset=GoogleMapsCompatible&tilematrix={z}&tilerow={y}&tilecol={x}"
    }

    val tileSet = TileSet("2.2.0", tileUrl)
    tileSet.attribution = if (baseMap == BaseMap.OSM) "© OpenStreetMap" else "© IGN PNOA"
    
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
            } catch (e: Exception) { e.printStackTrace() }
        }

        // --- RECINTO (Líneas) ---
        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 5f; tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                val lineLayer = LineLayer(LAYER_RECINTO_LINE, SOURCE_RECINTO)
                lineLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                lineLayer.setProperties(
                    PropertyFactory.lineColor(Color.White.toArgb()),
                    PropertyFactory.lineWidth(1.5f)
                )
                style.addLayer(lineLayer)
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (enableLocation(map, context, shouldCenterUser)) {
            onLocationEnabled()
        }
    }
}

/**
 * Activa o restaura la ubicación.
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

// Extension helper
fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}