
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.graphics.RectF
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import java.util.Locale

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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    // --- ESTADO DEL MAPA ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

    // Estado Búsqueda
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var showCustomKeyboard by remember { mutableStateOf(false) }

    // Estado de datos SIGPAC
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Asegurar inicialización de MapLibre una sola vez
    remember { 
        Log.d("NativeMap", "Inicializando MapLibre Singleton")
        MapLibre.getInstance(context) 
    }

    // El MapView se crea fuera para poder gestionar su ciclo de vida manualmente
    val mapView = remember { MapView(context) }

    // Observador del Ciclo de Vida para el MapView
    DisposableEffect(lifecycleOwner) {
        Log.d("NativeMap", "Suscribiendo observador de ciclo de vida")
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Log.d("NativeMap", "MapView onCreate")
                    mapView.onCreate(Bundle())
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("NativeMap", "MapView onDestroy")
                    mapInstance = null
                    mapView.onDestroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- LÓGICA DE ACTUALIZACIÓN VISUAL ---
    fun updateHighlightVisuals(map: MapLibreMap) {
        if (searchActive) return
        
        // Solo detectamos a partir de un zoom cercano
        if (map.cameraPosition.zoom < 13.5) {
            val emptyFilter = Expression.literal(false)
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
            return
        }

        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 12f, screenPoint.y - 12f, screenPoint.x + 12f, screenPoint.y + 12f)
        val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
        
        if (features.isNotEmpty()) {
            val props = features[0].properties()
            if (props != null) {
                val filterConditions = mutableListOf<Expression>()
                SIGPAC_KEYS.forEach { key ->
                    if (props.has(key)) {
                        val element = props.get(key)
                        val value: Any = if (element.isJsonPrimitive) {
                            val prim = element.asJsonPrimitive
                            when {
                                prim.isNumber -> prim.asNumber
                                prim.isBoolean -> prim.asBoolean
                                else -> prim.asString
                            }
                        } else element.toString()
                        filterConditions.add(Expression.eq(Expression.get(key), Expression.literal(value)))
                    }
                }
                val finalFilter = Expression.all(*filterConditions.toTypedArray())
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(finalFilter) }
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(finalFilter) }
            }
        } else {
            val emptyFilter = Expression.literal(false)
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
        }
    }

    // --- LÓGICA DE CARGA DE DATOS ---
    fun updateDataSheet(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13.5) {
            recintoData = null; cultivoData = null; lastDataId = null
            return
        }
        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 12f, screenPoint.y - 12f, screenPoint.x + 12f, screenPoint.y + 12f)
        
        val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
        if (features.isNotEmpty()) {
            val props = features[0].properties()
            val prov = props?.get("provincia")?.asString ?: ""
            val mun = props?.get("municipio")?.asString ?: ""
            val pol = props?.get("poligono")?.asString ?: ""
            val parc = props?.get("parcela")?.asString ?: ""
            val rec = props?.get("recinto")?.asString ?: ""
            val uniqueId = "$prov-$mun-$pol-$parc-$rec"
            
            if (uniqueId != lastDataId) {
                lastDataId = uniqueId
                recintoData = mapOf("provincia" to prov, "municipio" to mun, "poligono" to pol, "parcela" to parc, "recinto" to rec, "superficie" to (props?.get("superficie")?.toString() ?: "0"), "uso_sigpac" to "Consultando...")
                isLoadingData = true
                apiJob?.cancel()
                apiJob = scope.launch {
                    val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
                    if (fullData != null) recintoData = fullData
                    isLoadingData = false
                }
            }
        }

        // Consultar cultivos declarados si la capa está activa
        if (showCultivo) {
            val cultFeatures = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL)
            if (cultFeatures.isNotEmpty()) {
                val cultProps = cultFeatures[0].properties()
                if (cultProps != null) {
                    val mapProps = mutableMapOf<String, String>()
                    cultProps.entrySet().forEach { mapProps[it.key] = it.value.toString().replace("\"", "") }
                    cultivoData = mapProps
                }
            } else {
                cultivoData = null
            }
        }
    }

    // --- RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                if (mapInstance == null) {
                    view.getMapAsync { map ->
                        Log.d("NativeMap", "MapLibreMap listo")
                        mapInstance = map
                        
                        // Configuración inicial de cámara
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(VALENCIA_LAT, VALENCIA_LNG))
                            .zoom(DEFAULT_ZOOM)
                            .build()

                        map.addOnCameraMoveListener { updateHighlightVisuals(map) }
                        map.addOnCameraIdleListener { updateDataSheet(map) }

                        // Carga inicial del estilo
                        loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = true) {
                            Log.d("NativeMap", "Ubicación inicial activada")
                        }
                    }
                }
            }
        )

        // Cruz central (Puntero)
        if (!showCustomKeyboard) {
            Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.Black.copy(0.4f), modifier = Modifier.size(42.dp))
                Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }

        // --- BUSCADOR (TOP LEFT) ---
        Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp, end = 16.dp).fillMaxWidth(0.7f)) {
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { if (it is PressInteraction.Release) showCustomKeyboard = true }
            }
            TextField(
                value = searchQuery,
                onValueChange = { },
                placeholder = { Text("Prov:Mun:Pol:Parc", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                readOnly = true,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HighContrastWhite),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = FieldBackground.copy(alpha = 0.9f),
                    unfocusedContainerColor = FieldBackground.copy(alpha = 0.8f),
                    focusedIndicatorColor = FieldGreen,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; searchActive = false; lastDataId = null; recintoData = null }) { 
                            Icon(Icons.Default.Close, "Borrar", tint = Color.Gray) 
                        }
                    } else {
                        Icon(Icons.Default.Search, "Buscar", tint = FieldGreen)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
        }

        // --- CONTROLES FLOTANTES ---
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(onClick = { showLayerMenu = !showLayerMenu }, containerColor = Color.White) { Icon(Icons.Default.Settings, "Capas") }
            
            AnimatedVisibility(visible = showLayerMenu) {
                Card(modifier = Modifier.width(180.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mapa Base", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        BaseMap.values().forEach { base ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { currentBaseMap = base; showLayerMenu = false }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = (currentBaseMap == base), onClick = { currentBaseMap = base; showLayerMenu = false }, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(base.title, fontSize = 12.sp)
                            }
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }) {
                            Checkbox(checked = showRecinto, onCheckedChange = { showRecinto = it }, modifier = Modifier.size(24.dp)); Text("Recintos", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }) {
                            Checkbox(checked = showCultivo, onCheckedChange = { showCultivo = it }, modifier = Modifier.size(24.dp)); Text("Cultivos", fontSize = 12.sp)
                        }
                    }
                }
            }

            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = Color.White, contentColor = Color(0xFF006D3E)) { Icon(Icons.Default.List, "Proyectos") }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = Color.White, contentColor = Color(0xFF006D3E)) { Icon(Icons.Default.CameraAlt, "Cámara") }
            FloatingActionButton(onClick = { enableLocation(mapInstance, context, shouldCenter = true) }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.MyLocation, "Mi posición") }
        }

        // --- PANEL DE INFORMACIÓN ---
        if (!showCustomKeyboard) {
            AnimatedVisibility(
                visible = (recintoData != null || (cultivoData != null && showCultivo)),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val displayData = recintoData ?: cultivoData
                displayData?.let { data ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                            .pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -15) isPanelExpanded = true else if (dragAmount > 15) isPanelExpanded = false } },
                        colors = CardDefaults.cardColors(containerColor = FieldBackground.copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            // Handle
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.2f)))
                            }
                            
                            // Cabecera Info
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("PROV:MUN:POL:PARC:REC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("${data["provincia"]}:${data["municipio"]}:${data["poligono"]}:${data["parcela"]}:${data["recinto"]}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FieldGreen)
                                }
                                IconButton(onClick = { recintoData = null; cultivoData = null; lastDataId = null; isPanelExpanded = false }) { 
                                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White) 
                                }
                            }
                            
                            // Tabs
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(FieldSurface, RoundedCornerShape(12.dp)).padding(4.dp)) {
                                val tabs = listOf("Recinto", "Cultivo")
                                tabs.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) FieldGreen else Color.Transparent).clickable { selectedTab = index; isPanelExpanded = true }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Gray, fontSize = 13.sp)
                                    }
                                }
                            }

                            if (isPanelExpanded) {
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    if (selectedTab == 0 && recintoData != null) {
                                        val r = recintoData!!
                                        Row(Modifier.fillMaxWidth()) { AttributeItem("Uso", r["uso_sigpac"], Modifier.weight(1f)); AttributeItem("Superficie", "${r["superficie"]} ha", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(16.dp))
                                        Row(Modifier.fillMaxWidth()) { AttributeItem("Pendiente", "${r["pendiente_media"]}%", Modifier.weight(1f)); AttributeItem("Altitud", "${r["altitud"]} m", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(16.dp))
                                        AttributeItem("Incidencias", r["incidencias"] ?: "Ninguna", Modifier.fillMaxWidth())
                                    } else if (selectedTab == 1 && cultivoData != null) {
                                        val c = cultivoData!!
                                        AttributeItem("Producto", c["parc_producto"], Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(12.dp))
                                        Row(Modifier.fillMaxWidth()) { AttributeItem("Expediente", c["exp_num"], Modifier.weight(1f)); AttributeItem("Año", c["exp_ano"], Modifier.weight(1f)) }
                                    } else {
                                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                            Text("No hay datos disponibles para esta pestaña", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- TECLADO ---
        AnimatedVisibility(visible = showCustomKeyboard, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            CustomSigpacKeyboard(
                onKey = { searchQuery += it },
                onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                onSearch = { 
                    showCustomKeyboard = false
                    searchActive = true
                    isSearching = true
                    scope.launch {
                        val parts = searchQuery.split(":")
                        if (parts.size >= 4) {
                            val bbox = searchParcelLocation(parts[0], parts[1], parts[2], parts[3], parts.getOrNull(4))
                            if (bbox != null) mapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(bbox, 100))
                            else Toast.makeText(context, "No se encontró el recinto", Toast.LENGTH_SHORT).show()
                        }
                        isSearching = false
                    }
                },
                onClose = { showCustomKeyboard = false }
            )
        }
    }
}
