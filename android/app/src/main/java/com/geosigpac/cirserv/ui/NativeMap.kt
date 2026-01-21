
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.geosigpac.cirserv.model.NativeExpediente
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Locale

private const val TAG = "GeoSIGPAC_LOG_Map"
private const val SOURCE_KML_ID = "kml-project-source"
private const val LAYER_KML_FILL = "kml-project-layer-fill"
private const val LAYER_KML_LINE = "kml-project-layer-line"

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    targetRef: String?,
    expedientes: List<NativeExpediente>,
    onNavigateToProjects: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    // --- ESTADO ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }

    var initialLocationSet by remember { mutableStateOf(false) }

    // Estado Búsqueda
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    // Selector de Proyecto
    var selectedProject by remember { mutableStateOf<NativeExpediente?>(null) }
    var showProjectDropdown by remember { mutableStateOf(false) }
    
    // TECLADO PERSONALIZADO
    var showCustomKeyboard by remember { mutableStateOf(false) }

    // --- ESTADO DATOS ---
    var instantSigpacRef by remember { mutableStateOf("") }
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Inicializar MapLibre (Singleton)
    remember { MapLibre.getInstance(context) }

    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    // --- FUNCIÓN DE BÚSQUEDA ---
    fun performSearch() {
        if (searchQuery.isBlank()) return
        showCustomKeyboard = false
        focusManager.clearFocus()
        isSearching = true
        searchActive = true
        
        // La referencia viene normalizada: Prov:Mun:Pol:Parc:Rec
        val parts = searchQuery.split(":").map { it.trim() }
        if (parts.size < 4) {
            Toast.makeText(context, "Formato: Prov:Mun:Pol:Parc[:Rec]", Toast.LENGTH_LONG).show()
            isSearching = false
            return
        }

        val prov = parts[0]; val mun = parts[1]; val pol = parts[2]; val parc = parts[3]; val rec = parts.getOrNull(4)

        scope.launch {
            val map = mapInstance
            if (map != null && map.style != null) {
                val filterList = mutableListOf<Expression>(
                    Expression.eq(Expression.toString(Expression.get("provincia")), Expression.literal(prov)),
                    Expression.eq(Expression.toString(Expression.get("municipio")), Expression.literal(mun)),
                    Expression.eq(Expression.toString(Expression.get("poligono")), Expression.literal(pol)),
                    Expression.eq(Expression.toString(Expression.get("parcela")), Expression.literal(parc))
                )
                if (rec != null) {
                    filterList.add(Expression.eq(Expression.toString(Expression.get("recinto")), Expression.literal(rec)))
                }
                val filter = Expression.all(*filterList.toTypedArray())
                
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(filter) }
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(filter) }
            }

            // Nota: searchParcelLocation usa agregado=0 y zona=0 por defecto, lo cual es correcto
            val bbox = searchParcelLocation(prov, mun, pol, parc, rec)
            if (bbox != null) {
                mapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(bbox, 100), 1500)
            } else {
                Toast.makeText(context, "Ubicación no encontrada", Toast.LENGTH_SHORT).show()
            }
            
            isSearching = false
        }
    }

    // --- VISUALIZACIÓN DE PROYECTO (KML/GeoJSON) ---
    fun updateKmlLayer(project: NativeExpediente?) {
        val map = mapInstance ?: return
        val style = map.style ?: return

        // Limpiar capas anteriores
        if (style.getLayer(LAYER_KML_LINE) != null) style.removeLayer(LAYER_KML_LINE)
        if (style.getLayer(LAYER_KML_FILL) != null) style.removeLayer(LAYER_KML_FILL)
        if (style.getSource(SOURCE_KML_ID) != null) style.removeSource(SOURCE_KML_ID)

        if (project == null || project.parcelas.isEmpty()) return

        // Convertir parcelas a Features GeoJSON
        val features = project.parcelas.mapNotNull { parcela ->
            if (parcela.geometry != null) {
                Feature.fromJson(parcela.geometry)
            } else {
                Feature.fromGeometry(Point.fromLngLat(parcela.lng, parcela.lat))
            }
        }

        val source = GeoJsonSource(SOURCE_KML_ID, FeatureCollection.fromFeatures(features))
        style.addSource(source)

        // 1. Capa de Relleno (Polígonos Azules Semi-transparentes)
        val fillLayer = FillLayer(LAYER_KML_FILL, SOURCE_KML_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), // Azul Google
                PropertyFactory.fillOpacity(0.4f)
            )
        }
        style.addLayer(fillLayer)

        // 2. Capa de Línea (Borde Azul Claro)
        val lineLayer = LineLayer(LAYER_KML_LINE, SOURCE_KML_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color(0xFF64B5F6).toArgb()), // Azul más claro
                PropertyFactory.lineWidth(2f)
            )
        }
        style.addLayer(lineLayer)
    }

    // --- GESTIÓN ROBUSTA DEL CICLO DE VIDA ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            } catch (e: Exception) { Log.e(TAG, "Error en ciclo de vida MapView: ${e.message}") }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { mapView.onPause(); mapView.onStop(); mapView.onDestroy() } catch (e: Exception) { }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchActive = false
        lastDataId = null
        recintoData = null
        cultivoData = null
        instantSigpacRef = ""
        mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(Expression.literal(false)) }
        mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(Expression.literal(false)) }
    }

    fun updateRealtimeInfo(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13) {
            instantSigpacRef = ""
            return
        }
        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        try {
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val feature = features[0]
                val prov = feature.getStringProperty("provincia"); val mun = feature.getStringProperty("municipio")
                val pol = feature.getStringProperty("poligono"); val parc = feature.getStringProperty("parcela"); val rec = feature.getStringProperty("recinto")
                
                // Formato Estricto 5 partes: Prov:Mun:Pol:Parc:Rec (Sin Ag/Zn)
                if (prov != null && mun != null && pol != null && parc != null && rec != null) {
                    instantSigpacRef = "$prov:$mun:$pol:$parc:$rec"
                }
                
                val props = feature.properties()
                if (props != null) {
                    val filterConditions = mutableListOf<Expression>()
                    SIGPAC_KEYS.forEach { key ->
                        if (props.has(key)) {
                            val element = props.get(key)
                            val value: Any = when {
                                element.isJsonPrimitive -> { val prim = element.asJsonPrimitive; when { prim.isNumber -> prim.asNumber; prim.isBoolean -> prim.asBoolean; else -> prim.asString } }
                                else -> element.toString()
                            }
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
                instantSigpacRef = ""
            }
        } catch (e: Exception) { }
    }

    fun updateExtendedData(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13) { recintoData = null; cultivoData = null; lastDataId = null; return }
        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        try {
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val props = features[0].properties()
                val uniqueId = "${props?.get("provincia")}-${props?.get("recinto")}"
                if (uniqueId != lastDataId) {
                    lastDataId = uniqueId
                    recintoData = mapOf("provincia" to (props?.get("provincia")?.asString ?: ""), "municipio" to (props?.get("municipio")?.asString ?: ""), "uso_sigpac" to "Cargando...", "superficie" to (props?.get("superficie")?.toString() ?: "0"))
                    isLoadingData = true
                    apiJob?.cancel()
                    apiJob = scope.launch {
                        delay(200)
                        val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
                        if (fullData != null) { 
                            recintoData = fullData
                            // Construcción de referencia 5 partes
                            instantSigpacRef = "${fullData["provincia"]}:${fullData["municipio"]}:${fullData["poligono"]}:${fullData["parcela"]}:${fullData["recinto"]}"
                        }
                        isLoadingData = false
                    }
                }
            } else { lastDataId = null }
            val cultFeatures = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL)
            if (cultFeatures.isNotEmpty()) {
                 val props = cultFeatures[0].properties()
                 if (props != null) {
                    val mapProps = mutableMapOf<String, String>()
                    props.entrySet().forEach { mapProps[it.key] = it.value.toString().replace("\"", "") }
                    cultivoData = mapProps
                }
            } else { cultivoData = null }
        } catch (e: Exception) { }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync(OnMapReadyCallback { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false
            if (!initialLocationSet) { map.cameraPosition = CameraPosition.Builder().target(LatLng(VALENCIA_LAT, VALENCIA_LNG)).zoom(DEFAULT_ZOOM).build() }
            map.addOnCameraMoveListener { updateRealtimeInfo(map) }
            map.addOnCameraIdleListener { updateExtendedData(map) }
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) { initialLocationSet = true }
        })
    }

    // Efecto para buscar automáticamente si llega una referencia desde "Localizar"
    LaunchedEffect(targetRef) {
        if (!targetRef.isNullOrEmpty()) {
            searchQuery = targetRef
            performSearch()
        }
    }

    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map -> loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) { } }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Cruz central
        if (!isSearching && !showCustomKeyboard) {
            Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.Black.copy(0.5f), modifier = Modifier.size(38.dp))
                Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // --- PANEL SUPERIOR IZQUIERDA (BUSCADOR + DROPDOWN PROYECTO) ---
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp, end = 16.dp).fillMaxWidth(0.65f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Buscador
            Box {
                val interactionSource = remember { MutableInteractionSource() }
                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) { showCustomKeyboard = true; isPanelExpanded = false }
                    }
                }
                TextField(
                    value = searchQuery,
                    onValueChange = { },
                    placeholder = { Text("Prov:Mun:Pol:Parc", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    maxLines = 1,
                    readOnly = true,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = HighContrastWhite),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = FieldBackground.copy(alpha = 0.9f),
                        unfocusedContainerColor = FieldBackground.copy(alpha = 0.7f),
                        focusedIndicatorColor = if(showCustomKeyboard) FieldGreen else Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = FieldGreen
                    ),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { clearSearch(); showCustomKeyboard = false }) { Icon(Icons.Default.Close, "Borrar", tint = Color.Gray) }
                        } else {
                            IconButton(onClick = { showCustomKeyboard = true }) { Icon(Icons.Default.Search, "Buscar", tint = FieldGreen) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }

            // --- SELECTOR DE PROYECTO KML / RECINTOS ---
            Box {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showProjectDropdown = true },
                    // Color base oscuro y opaco
                    colors = CardDefaults.cardColors(containerColor = if(selectedProject != null) Color(0xFF13141F).copy(alpha = 0.95f) else Color.Transparent),
                    shape = RoundedCornerShape(12.dp),
                    border = if(selectedProject != null) BorderStroke(1.dp, Color(0xFF2196F3).copy(0.5f)) else null
                ) {
                    if (selectedProject != null || expedientes.isNotEmpty()) {
                         Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, tint = if(selectedProject != null) Color(0xFF2196F3) else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = selectedProject?.titular ?: "Seleccionar Proyecto...",
                                color = if(selectedProject != null) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                        }
                    }
                }

                DropdownMenu(
                    expanded = showProjectDropdown,
                    onDismissRequest = { showProjectDropdown = false },
                    // Fondo OPACO
                    containerColor = Color(0xFF1A1C1E),
                    modifier = Modifier.widthIn(min = 220.dp, max = 300.dp).heightIn(max = 400.dp)
                ) {
                    if (selectedProject == null) {
                        // MODO: SELECCIONAR PROYECTO
                        DropdownMenuItem(
                            text = { Text("PROYECTOS DISPONIBLES", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            onClick = { }
                        )
                        expedientes.forEach { exp ->
                            DropdownMenuItem(
                                text = { Text(exp.titular, color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    selectedProject = exp
                                    showProjectDropdown = false 
                                    updateKmlLayer(exp)
                                    // Centrar en el primer recinto
                                    if (exp.parcelas.isNotEmpty()) {
                                        val p = exp.parcelas.first()
                                        mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 13.0))
                                    }
                                }
                            )
                        }
                        if (expedientes.isEmpty()) {
                            DropdownMenuItem(text = { Text("No hay proyectos cargados", color = Color.Gray, fontSize = 12.sp) }, onClick = {})
                        }
                    } else {
                        // MODO: SELECCIONAR RECINTO DEL PROYECTO
                        DropdownMenuItem(
                            text = { Text("🔙 CAMBIAR PROYECTO", color = Color(0xFF2196F3), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            onClick = { 
                                selectedProject = null
                                updateKmlLayer(null)
                            }
                        )
                        Divider(color = Color.White.copy(0.1f))
                        selectedProject!!.parcelas.forEach { parcela ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(parcela.referencia, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(parcela.uso, color = Color.Gray, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    showProjectDropdown = false
                                    // BÚSQUEDA AUTOMÁTICA USANDO LA REFERENCIA
                                    searchQuery = parcela.referencia
                                    performSearch()
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- BOTONES (TOP END) ---
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(onClick = { showLayerMenu = !showLayerMenu }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.Settings, "Capas") }

            AnimatedVisibility(visible = showLayerMenu) {
                Card(modifier = Modifier.width(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mapa Base", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        BaseMap.values().forEach { base ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { currentBaseMap = base }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = (currentBaseMap == base), onClick = { currentBaseMap = base }, modifier = Modifier.size(20.dp), colors = RadioButtonDefaults.colors(selectedColor = FieldGreen))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(base.title, fontSize = 13.sp)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Capas SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showRecinto = !showRecinto }) {
                            Checkbox(checked = showRecinto, onCheckedChange = { showRecinto = it }, modifier = Modifier.size(30.dp).padding(4.dp), colors = CheckboxDefaults.colors(checkedColor = FieldGreen)); Text("Recintos", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showCultivo = !showCultivo }) {
                            Checkbox(checked = showCultivo, onCheckedChange = { showCultivo = it }, modifier = Modifier.size(30.dp).padding(4.dp), colors = CheckboxDefaults.colors(checkedColor = FieldGreen)); Text("Cultivos", fontSize = 13.sp)
                        }
                    }
                }
            }
            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.List, "Proyectos") }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.CameraAlt, "Cámara") }
            SmallFloatingActionButton(onClick = { 
                mapInstance?.let { map ->
                    com.geosigpac.cirserv.ui.enableLocation(map, context, true)
                }
            }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.MyLocation, "Ubicación") }
        }

        // Loading
        if (isLoadingData || isSearching) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
                CircularProgressIndicator(color = FieldGreen, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
            }
        }

        // BOTTOM SHEET (Info)
        if (!showCustomKeyboard) {
            val showSheet = instantSigpacRef.isNotEmpty() || recintoData != null || (cultivoData != null && showCultivo)
            AnimatedVisibility(visible = showSheet, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
                val displayData = recintoData ?: mapOf("provincia" to "", "municipio" to "")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(0.dp).animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        .pointerInput(Unit) { detectVerticalDragGestures { change, dragAmount -> change.consume(); if (dragAmount < -20) isPanelExpanded = true else if (dragAmount > 20) isPanelExpanded = false } },
                    colors = CardDefaults.cardColors(containerColor = FieldBackground.copy(alpha = 0.98f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color.White.copy(alpha = 0.3f)))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text("REF. SIGPAC", style = MaterialTheme.typography.labelSmall, color = FieldGray)
                                Text(
                                    text = if (instantSigpacRef.isNotEmpty()) instantSigpacRef else "${displayData["provincia"]}:${displayData["municipio"]}...",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FieldGreen
                                )
                            }
                            IconButton(onClick = { instantSigpacRef = ""; recintoData = null; cultivoData = null; isPanelExpanded = false; clearSearch() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Cerrar", tint = HighContrastWhite) }
                        }
                        if (recintoData != null) {
                            Divider(color = FieldDivider)
                            val hasCultivo = cultivoData != null
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).background(FieldSurface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedTab == 0) FieldGreen else Color.Transparent).clickable { selectedTab = 0; isPanelExpanded = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("Recinto", fontWeight = FontWeight.Bold, color = if(selectedTab == 0) Color.White else FieldGray)
                                }
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedTab == 1) FieldGreen else Color.Transparent).clickable(enabled = hasCultivo) { if (hasCultivo) { selectedTab = 1; isPanelExpanded = true } }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("Cultivo", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else if (hasCultivo) FieldGray else Color.White.copy(alpha = 0.2f))
                                }
                            }
                            if (isPanelExpanded) {
                                Divider(color = FieldDivider)
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    if (selectedTab == 0 && recintoData != null) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Uso SIGPAC", recintoData!!["uso_sigpac"], Modifier.weight(1f)); AttributeItem("Superficie", "${recintoData!!["superficie"]} ha", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(12.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Pendiente Media", "${recintoData!!["pendiente_media"]}%", Modifier.weight(1f)); AttributeItem("Altitud", "${recintoData!!["altitud"]} m", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(12.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Región", recintoData!!["region"], Modifier.weight(1f)); AttributeItem("Coef. Regadío", "${recintoData!!["coef_regadio"]}%", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(12.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Subvencionabilidad", "${recintoData!!["subvencionabilidad"]}%", Modifier.weight(1f)); AttributeItem("Incidencias", recintoData!!["incidencias"]?.takeIf { it.isNotEmpty() } ?: "Ninguna", Modifier.weight(1f)) }
                                    } else if (cultivoData != null) {
                                        val c = cultivoData!!
                                        Text("Datos de Expediente", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                        Row(Modifier.fillMaxWidth()) { AttributeItem("Núm. Exp", c["exp_num"], Modifier.weight(1f)); AttributeItem("Año", c["exp_ano"], Modifier.weight(1f)) }
                                        Divider(Modifier.padding(vertical=6.dp), color = FieldDivider)
                                        Text("Datos Agrícolas", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                        Row(Modifier.fillMaxWidth()) { AttributeItem("Producto", c["parc_producto"], Modifier.weight(1f)); val supCultRaw = c["parc_supcult"]?.toDoubleOrNull() ?: 0.0; val supCultHa = supCultRaw / 10000.0; AttributeItem("Superficie", "${String.format(Locale.US, "%.4f", supCultHa)} ha", Modifier.weight(1f)) }
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth()) { val sist = c["parc_sistexp"]; val sistLabel = when(sist) { "S" -> "Secano"; "R" -> "Regadío"; else -> sist }; AttributeItem("Sist. Expl.", sistLabel, Modifier.weight(1f)); AttributeItem("Ind. Cultivo", c["parc_indcultapro"], Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TECLADO PERSONALIZADO
        AnimatedVisibility(visible = showCustomKeyboard, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            CustomSigpacKeyboard(
                onKey = { char -> searchQuery += char },
                onBackspace = { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                onSearch = { performSearch() },
                onClose = { showCustomKeyboard = false }
            )
        }
    }
}
