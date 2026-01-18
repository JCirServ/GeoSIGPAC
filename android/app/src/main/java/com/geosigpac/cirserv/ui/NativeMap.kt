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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private const val LAYER_RECINTO_FILL = "recinto-layer-fill" // Nueva capa invisible para detección
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
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

    // Estado UI Panel
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Recinto, 1: Cultivo

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
            map.uiSettings.isTiltGesturesEnabled = false

            // Posición inicial por defecto
            if (!initialLocationSet) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(VALENCIA_LAT, VALENCIA_LNG))
                    .zoom(DEFAULT_ZOOM)
                    .build()
            }

            // LISTENER: Al empezar a mover
            map.addOnCameraMoveStartedListener {
                apiJob?.cancel()
                isLoadingData = false
            }

            // LISTENER: Al detenerse
            map.addOnCameraIdleListener {
                val center = map.cameraPosition.target
                if (center != null && map.cameraPosition.zoom > 13) {
                    
                    val screenPoint = map.projection.toScreenLocation(center)
                    
                    // --- DEBUG LOGS (MVT INSPECTOR) ---
                    val features = map.queryRenderedFeatures(screenPoint)
                    Log.i("MVT_INSPECTOR", "===========================================================")
                    Log.i("MVT_INSPECTOR", "DATOS MVT EN CENTRO: Lat ${center.latitude}, Lng ${center.longitude}")
                    if (features.isEmpty()) {
                        Log.i("MVT_INSPECTOR", "No se encontraron features vectoriales aquí.")
                    } else {
                        features.forEachIndexed { index, feature ->
                            Log.i("MVT_INSPECTOR", "Feature #$index detectada:")
                            val props = feature.properties()
                            if (props != null) {
                                props.entrySet().forEach { entry ->
                                    Log.d("MVT_INSPECTOR", "   > [${entry.key}] = ${entry.value}")
                                }
                            }
                        }
                    }
                    Log.i("MVT_INSPECTOR", "===========================================================")
                    // ----------------------------------

                    isLoadingData = true
                    
                    // 1. EXTRAER DATOS DE CULTIVO (MVT) - Síncrono
                    val cultFeatures = map.queryRenderedFeatures(screenPoint, LAYER_CULTIVO_FILL)
                    if (cultFeatures.isNotEmpty()) {
                        val props = cultFeatures[0].properties()
                        if (props != null) {
                            val mapProps = mutableMapOf<String, String>()
                            props.entrySet().forEach { 
                                mapProps[it.key] = it.value.toString().replace("\"", "") 
                            }
                            cultivoData = mapProps
                        } else {
                            cultivoData = null
                        }
                    } else {
                        cultivoData = null
                    }
                    
                    // Resetear tab si no hay cultivo
                    if (cultivoData == null && selectedTab == 1) {
                        selectedTab = 0
                    }

                    // 2. EXTRAER DATOS DE RECINTO (API) - Asíncrono
                    apiJob?.cancel()
                    apiJob = scope.launch {
                        delay(300) 
                        val data = fetchFullSigpacInfo(center.latitude, center.longitude)
                        recintoData = data
                        isLoadingData = false
                    }
                } else {
                    recintoData = null
                    cultivoData = null
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
                        .zoom(18.0)
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

        // --- CRUZ CENTRAL ---
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = Color.Black.copy(0.5f), modifier = Modifier.size(38.dp))
            Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // --- COLUMNA DE CONTROLES ---
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showLayerMenu = !showLayerMenu },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { Icon(Icons.Default.Settings, "Capas") }

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
            
            SmallFloatingActionButton(
                onClick = onNavigateToProjects,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E),
                shape = CircleShape
            ) { Icon(Icons.Default.List, "Proyectos") }
            
            SmallFloatingActionButton(
                onClick = onOpenCamera,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF006D3E),
                shape = CircleShape
            ) { Icon(Icons.Default.CameraAlt, "Cámara") }

            SmallFloatingActionButton(
                onClick = { enableLocation(mapInstance, context, shouldCenter = true) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Default.MyLocation, "Ubicación") }
        }

        // --- LOADING ---
        if (isLoadingData) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
            }
        }

        // --- PANEL DE DATOS INFERIOR ---
        AnimatedVisibility(
            visible = recintoData != null || (cultivoData != null && showCultivo),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val displayData = recintoData ?: cultivoData
            displayData?.let { data ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .heightIn(max = 500.dp), // Aumentamos altura máxima para mostrar todos los datos
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // CABECERA
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            IconButton(onClick = { recintoData = null; cultivoData = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Cerrar")
                            }
                        }
                        
                        Divider()

                        // PESTAÑAS
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 0 }
                                    .background(if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color(0xFFEEEEEE))
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Recinto", fontWeight = if(selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if(selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Gray)
                                if (selectedTab == 0) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
                            }

                            val hasCultivo = cultivoData != null
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = hasCultivo) { if (hasCultivo) selectedTab = 1 }
                                    .background(if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color(0xFFEEEEEE))
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cultivo", fontWeight = if(selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else if (hasCultivo) Color.DarkGray else Color.LightGray.copy(alpha=0.6f))
                                if (selectedTab == 1) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                        
                        Divider()

                        // CONTENIDO
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            if (selectedTab == 0) {
                                // --- VISTA RECINTO ---
                                if (recintoData != null) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        AttributeItem("Uso SIGPAC", recintoData!!["uso_sigpac"], Modifier.weight(1f))
                                        // CAMBIO: Se muestra en áreas, sin conversión a Ha
                                        AttributeItem("Superficie", "${recintoData!!["superficie"]} áreas", Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        AttributeItem("Pendiente Media", "${recintoData!!["pendiente_media"]}%", Modifier.weight(1f))
                                        AttributeItem("Altitud", "${recintoData!!["altitud"]} m", Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        AttributeItem("Región", recintoData!!["region"], Modifier.weight(1f))
                                        AttributeItem("Coef. Regadío", "${recintoData!!["coef_regadio"]}%", Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        AttributeItem("Subvencionabilidad", "${recintoData!!["subvencionabilidad"]}%", Modifier.weight(1f))
                                        AttributeItem("Incidencias", recintoData!!["incidencias"]?.takeIf { it.isNotEmpty() } ?: "Ninguna", Modifier.weight(1f))
                                    }
                                } else {
                                    Text("Cargando datos de recinto...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            } else {
                                // --- VISTA CULTIVO (EXPANDIDA) ---
                                if (cultivoData != null) {
                                    val c = cultivoData!!
                                    
                                    // 1. Identificación SIGPAC (Desde capa cultivo)
                                    Text("Identificación (Capa Cultivo)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    val refString = "${c["provincia"]}/${c["municipio"]}/${c["agregado"]}/${c["zona"]}/${c["poligono"]}/${c["parcela"]}/${c["recinto"]}"
                                    Text(refString, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=8.dp))
                                    
                                    Divider(Modifier.padding(vertical=6.dp))

                                    // 2. Expediente
                                    Text("Datos de Expediente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical=4.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        AttributeItem("Núm. Exp", c["exp_num"], Modifier.weight(1f))
                                        AttributeItem("Año", c["exp_ano"], Modifier.weight(1f))
                                        AttributeItem("Prov. Exp", c["exp_provincia"], Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        AttributeItem("CA Exp", c["exp_ca"], Modifier.weight(1f))
                                        Spacer(Modifier.weight(2f)) // Espaciador para alinear
                                    }
                                    
                                    Divider(Modifier.padding(vertical=6.dp))

                                    // 3. Datos Cultivo
                                    Text("Datos Agrícolas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical=4.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        AttributeItem("Producto", c["parc_producto"], Modifier.weight(1f))
                                        
                                        // CORRECCIÓN SOLICITADA: Dividir entre 10000
                                        val supCultRaw = c["parc_supcult"]?.toDoubleOrNull() ?: 0.0
                                        val supCultHa = supCultRaw / 10000.0
                                        AttributeItem("Superficie", "${String.format(Locale.US, "%.4f", supCultHa)} ha", Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        val sist = c["parc_sistexp"]
                                        val sistLabel = when(sist) { "S" -> "Secano"; "R" -> "Regadío"; else -> sist }
                                        AttributeItem("Sist. Expl.", sistLabel, Modifier.weight(1f))
                                        AttributeItem("Ind. Cultivo", c["parc_indcultapro"], Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    AttributeItem("Tipo Aprovechamiento", c["tipo_aprovecha"], Modifier.fillMaxWidth())

                                    Divider(Modifier.padding(vertical=6.dp))
                                    
                                    // 4. Ayudas
                                    Text("Ayudas Solicitadas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical=4.dp))
                                    AttributeItem("Ayudas Parc.", c["parc_ayudasol"], Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(4.dp))
                                    AttributeItem("Ayudas PDR", c["pdr_rec"], Modifier.fillMaxWidth())

                                    Divider(Modifier.padding(vertical=6.dp))

                                    // 5. Cultivo Secundario
                                    Text("Cultivo Secundario", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical=4.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        AttributeItem("Producto Sec.", c["cultsecun_producto"], Modifier.weight(1f))
                                        AttributeItem("Ayuda Sec.", c["cultsecun_ayudasol"], Modifier.weight(1f))
                                    }
                                }
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
        Text(
            text = if (value.isNullOrEmpty() || value == "null" || value == "0") "-" else value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.SemiBold
        )
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
            Log.d("API_SIGPAC", "Respuesta JSON: $jsonResponse")

            var targetJson: JSONObject? = null
            
            if (jsonResponse.startsWith("[")) {
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) targetJson = jsonArray.getJSONObject(0)
            } else if (jsonResponse.startsWith("{")) {
                targetJson = JSONObject(jsonResponse)
            }

            if (targetJson != null) {
                fun getProp(key: String): String {
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    val props = targetJson!!.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optString(key)
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
                
                if (prov.isEmpty() || mun.isEmpty() || pol.isEmpty()) return@withContext null

                // --- CORRECCIÓN SUPERFICIE ---
                // CAMBIO: No dividimos por 10000. Se trata como "áreas".
                val superficieRaw = getProp("superficie")
                
                // --- CORRECCIÓN ALTITUD ---
                var altitudVal = getProp("altitud")
                if (altitudVal.isEmpty()) {
                    altitudVal = getProp("altitud_media")
                }

                return@withContext mapOf(
                    "provincia" to prov,
                    "municipio" to mun,
                    "agregado" to getProp("agregado"),
                    "zona" to getProp("zona"),
                    "poligono" to pol,
                    "parcela" to getProp("parcela"),
                    "recinto" to getProp("recinto"),
                    "superficie" to superficieRaw, // Raw value
                    "pendiente_media" to getProp("pendiente_media"),
                    "altitud" to altitudVal,
                    "uso_sigpac" to getProp("uso_sigpac"),
                    "subvencionabilidad" to getProp("coef_admisibilidad_pastos"),
                    "coef_regadio" to getProp("coef_regadio"),
                    "incidencias" to getProp("incidencias").replace("[", "").replace("]", "").replace("\"", ""),
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

        if (showRecinto) {
            try {
                val recintoUrl = "https://sigpac-hubcloud.es/mvt/recinto@3857@pbf/{z}/{x}/{y}.pbf"
                val tileSetRecinto = TileSet("pbf", recintoUrl)
                tileSetRecinto.minZoom = 5f; tileSetRecinto.maxZoom = 15f

                val recintoSource = VectorSource(SOURCE_RECINTO, tileSetRecinto)
                style.addSource(recintoSource)

                val fillLayer = FillLayer(LAYER_RECINTO_FILL, SOURCE_RECINTO)
                fillLayer.sourceLayer = SOURCE_LAYER_ID_RECINTO
                fillLayer.setProperties(
                    PropertyFactory.fillColor(Color.Transparent.toArgb()),
                    PropertyFactory.fillOpacity(0.01f)
                )
                style.addLayer(fillLayer)

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

fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}