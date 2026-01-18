package com.geosigpac.cirserv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
    
    // Estado de expansión del panel inferior
    var isPanelExpanded by remember { mutableStateOf(false) }
    
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
                    
                    isLoadingData = true
                    // Al iniciar nueva carga, colapsamos el panel para que "emerja" de nuevo si es necesario
                    // Opcional: Si prefieres que se mantenga abierto si ya lo estaba, comenta la línea de abajo.
                    isPanelExpanded = false
                    
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

        // --- PANEL DE DATOS INFERIOR (BOTTOM SHEET) ---
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
                        .fillMaxWidth() // Ocultar todo el ancho
                        .padding(0.dp) // Sin padding externo
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)), // Animación suave al expandir
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                    // Esquinas superiores redondeadas, inferiores cuadradas
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. CABECERA (Siempre visible)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text("REF. SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                // CAMBIO: Separador : en lugar de /
                                Text(
                                    text = "${data["provincia"]}:${data["municipio"]}:${data["agregado"]}:${data["zona"]}:${data["poligono"]}:${data["parcela"]}:${data["recinto"]}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { recintoData = null; cultivoData = null; isPanelExpanded = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Cerrar")
                            }
                        }
                        
                        Divider(color = Color.LightGray.copy(alpha=0.4f))

                        // 2. PESTAÑAS (Estilo Cápsula/Pill)
                        val hasCultivo = cultivoData != null
                        
                        // Contenedor de fondo para las pestañas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(50)) // Forma redondeada general
                                .background(Color(0xFFF0F0F0)) // Fondo gris claro
                                .padding(4.dp), // Padding interno para las "pills"
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Pestaña RECINTO
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { 
                                        selectedTab = 0 
                                        isPanelExpanded = true 
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Recinto", 
                                    fontWeight = FontWeight.SemiBold, 
                                    color = if(selectedTab == 0) Color.White else Color.Gray
                                )
                            }

                            // Pestaña CULTIVO
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(enabled = hasCultivo) { 
                                        if (hasCultivo) {
                                            selectedTab = 1
                                            isPanelExpanded = true
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Cultivo", 
                                    fontWeight = FontWeight.SemiBold, 
                                    color = if (selectedTab == 1) Color.White else if (hasCultivo) Color.Gray else Color.LightGray
                                )
                            }
                        }
                        
                        // 3. CONTENIDO (Solo visible si está expandido)
                        if (isPanelExpanded) {
                            Divider(color = Color.LightGray.copy(alpha=0.4f))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp) 
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                if (selectedTab == 0) {
                                    // --- VISTA RECINTO ---
                                    if (recintoData != null) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            AttributeItem("Uso SIGPAC", recintoData!!["uso_sigpac"], Modifier.weight(1f))
                                            // CAMBIO: Valor original pero unidad 'ha'
                                            AttributeItem("Superficie", "${recintoData!!["superficie"]} ha", Modifier.weight(1f))
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
                                        
                                        // CAMBIO: Eliminada sección de Identificación

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
                                            
                                            // Cultivo: Dividir entre 10000 (Hectáreas) - Se mantiene como estaba
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