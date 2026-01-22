
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
import androidx.compose.material.icons.filled.Folder
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Polygon
import org.maplibre.geojson.Point
import java.util.Locale

private const val TAG = "GeoSIGPAC_LOG_Map"
private const val SOURCE_PROJECTS = "projects-source"
private const val LAYER_PROJECTS_FILL = "projects-fill"
private const val LAYER_PROJECTS_LINE = "projects-line"

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    expedientes: List<NativeExpediente>,
    targetLat: Double?,
    targetLng: Double?,
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
    var showProjectsMenu by remember { mutableStateOf(false) }

    // Proyectos visibles (Ids)
    var visibleProjectIds by remember { mutableStateOf<Set<String>>(expedientes.map { it.id }.toSet()) }

    var initialLocationSet by remember { mutableStateOf(false) }

    // Estado Búsqueda
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    
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

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
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
            } catch (e: Exception) {
                Log.e(TAG, "Error en ciclo de vida MapView: ${e.message}")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up MapView: ${e.message}")
            }
        }
    }

    // --- HELPER PARA GEOJSON DE PROYECTOS ---
    suspend fun updateProjectsLayer(map: MapLibreMap, currentExpedientes: List<NativeExpediente>, visibleIds: Set<String>) = withContext(Dispatchers.Default) {
        val features = mutableListOf<Feature>()
        
        currentExpedientes.filter { visibleIds.contains(it.id) }.forEach { exp ->
            exp.parcelas.forEach { p ->
                if (p.geometryRaw != null) {
                    try {
                        // Formato raw: "lng,lat,z lng,lat,z ..." separados por espacios
                        val points = mutableListOf<Point>()
                        val coordPairs = p.geometryRaw.trim().split("\\s+".toRegex())
                        
                        coordPairs.forEach { pair ->
                            val coords = pair.split(",")
                            if (coords.size >= 2) {
                                val lng = coords[0].toDoubleOrNull()
                                val lat = coords[1].toDoubleOrNull()
                                if (lng != null && lat != null) {
                                    points.add(Point.fromLngLat(lng, lat))
                                }
                            }
                        }
                        
                        if (points.isNotEmpty()) {
                            // MapLibre Polygon necesita una lista de listas de puntos (anillos)
                            val polygon = Polygon.fromLngLats(listOf(points))
                            val feature = Feature.fromGeometry(polygon)
                            feature.addStringProperty("ref", p.referencia)
                            feature.addStringProperty("exp", exp.titular)
                            features.add(feature)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val collection = FeatureCollection.fromFeatures(features)
        
        withContext(Dispatchers.Main) {
            map.style?.getSourceAs<GeoJsonSource>(SOURCE_PROJECTS)?.setGeoJson(collection)
        }
    }

    // --- FUNCIÓN DE BÚSQUEDA ---
    fun performSearch() {
        if (searchQuery.isBlank()) return
        showCustomKeyboard = false
        focusManager.clearFocus()
        isSearching = true
        searchActive = true
        
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

            val bbox = searchParcelLocation(prov, mun, pol, parc, rec)
            if (bbox != null) {
                mapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(bbox, 100), 1500)
            } else {
                Toast.makeText(context, "Ubicación no encontrada", Toast.LENGTH_SHORT).show()
            }
            
            isSearching = false
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

    // --- LÓGICA DE ACTUALIZACIÓN VISUAL Y REFERENCIA REAL-TIME ---
    fun updateRealtimeInfo(map: MapLibreMap) {
        if (searchActive) return
        
        if (map.cameraPosition.zoom < 13) {
            val emptyFilter = Expression.literal(false)
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
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
                val prov = feature.getStringProperty("provincia")
                val mun = feature.getStringProperty("municipio")
                val agg = feature.getStringProperty("agregado") ?: "0"
                val zon = feature.getStringProperty("zona") ?: "0"
                val pol = feature.getStringProperty("poligono")
                val parc = feature.getStringProperty("parcela")
                val rec = feature.getStringProperty("recinto")

                if (prov != null && mun != null && pol != null && parc != null && rec != null) {
                    instantSigpacRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
                }

                val props = feature.properties()
                if (props != null) {
                    val filterConditions = mutableListOf<Expression>()
                    SIGPAC_KEYS.forEach { key ->
                        if (props.has(key)) {
                            val element = props.get(key)
                            val value: Any = when {
                                element.isJsonPrimitive -> {
                                    val prim = element.asJsonPrimitive
                                    when {
                                        prim.isNumber -> prim.asNumber
                                        prim.isBoolean -> prim.asBoolean
                                        else -> prim.asString
                                    }
                                }
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
        } catch (e: Exception) { Log.e(TAG, "Error realtime update: ${e.message}") }
    }

    fun updateExtendedData(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13) {
            recintoData = null; cultivoData = null; lastDataId = null
            return
        }
        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        
        try {
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
                    recintoData = mapOf(
                        "provincia" to prov, "municipio" to mun, "poligono" to pol, "parcela" to parc, "recinto" to rec,
                        "agregado" to (props?.get("agregado")?.asString ?: "0"),
                        "zona" to (props?.get("zona")?.asString ?: "0"),
                        "superficie" to (props?.get("superficie")?.toString() ?: "0"),
                        "uso_sigpac" to "Cargando...", "pendiente_media" to "-", "altitud" to "-", "region" to "-",
                        "coef_regadio" to "-", "subvencionabilidad" to "-", "incidencias" to ""
                    )
                    isLoadingData = true
                    isPanelExpanded = false
                    apiJob?.cancel()
                    apiJob = scope.launch {
                        delay(200)
                        val fullData = fetchFullSigpacInfo(center.latitude, center.longitude)
                        if (fullData != null) {
                             recintoData = fullData
                             instantSigpacRef = "${fullData["provincia"]}:${fullData["municipio"]}:${fullData["agregado"]}:${fullData["zona"]}:${fullData["poligono"]}:${fullData["parcela"]}:${fullData["recinto"]}"
                        }
                        isLoadingData = false
                    }
                }
            } else {
                lastDataId = null
            }
            
            val cultFeatures = map.queryRenderedFeatures(searchArea, LAYER_CULTIVO_FILL)
            if (cultFeatures.isNotEmpty()) {
                 val props = cultFeatures[0].properties()
                 if (props != null) {
                    val mapProps = mutableMapOf<String, String>()
                    props.entrySet().forEach { mapProps[it.key] = it.value.toString().replace("\"", "") }
                    cultivoData = mapProps
                }
            } else {
                cultivoData = null
            }
        } catch (e: Exception) { Log.e(TAG, "Error querying extended features: ${e.message}") }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync(OnMapReadyCallback { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false

            if (!initialLocationSet) {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(VALENCIA_LAT, VALENCIA_LNG)).zoom(DEFAULT_ZOOM).build()
            }

            map.addOnCameraMoveListener { updateRealtimeInfo(map) }
            map.addOnCameraIdleListener { updateExtendedData(map) }

            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
                initialLocationSet = true
            }

            // ADD PROJECTS LAYERS ONCE STYLE LOADED
            map.getStyle { style ->
                // Source
                val geoJsonSource = GeoJsonSource(SOURCE_PROJECTS)
                style.addSource(geoJsonSource)

                // Fill Layer (Blue semi-transparent)
                val projectFill = FillLayer(LAYER_PROJECTS_FILL, SOURCE_PROJECTS)
                projectFill.setProperties(
                    PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), // Blue 500
                    PropertyFactory.fillOpacity(0.4f)
                )
                // Insertar debajo de las etiquetas si es posible, o simplemente arriba de la base
                style.addLayer(projectFill)

                // Line Layer (Darker Blue)
                val projectLine = LineLayer(LAYER_PROJECTS_LINE, SOURCE_PROJECTS)
                projectLine.setProperties(
                    PropertyFactory.lineColor(Color(0xFF0D47A1).toArgb()), // Blue 900
                    PropertyFactory.lineWidth(2f)
                )
                style.addLayer(projectLine)

                // Initial Load
                scope.launch {
                    updateProjectsLayer(map, expedientes, visibleProjectIds)
                }
            }
        })
    }

    // Effect for updating project geometry when list or visibility changes
    LaunchedEffect(expedientes, visibleProjectIds) {
        mapInstance?.let { map ->
            if (map.style != null && map.style!!.isFullyLoaded) {
                 updateProjectsLayer(map, expedientes, visibleProjectIds)
            }
        }
    }

    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapInstance?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(targetLat, targetLng)).zoom(18.0).tilt(0.0).build()), 1500)
        }
    }

    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) { }
            // Re-add project layers after style reload
            map.getStyle { style ->
                 if (style.getSource(SOURCE_PROJECTS) == null) {
                    val geoJsonSource = GeoJsonSource(SOURCE_PROJECTS)
                    style.addSource(geoJsonSource)

                    val projectFill = FillLayer(LAYER_PROJECTS_FILL, SOURCE_PROJECTS)
                    projectFill.setProperties(PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), PropertyFactory.fillOpacity(0.4f))
                    style.addLayer(projectFill)

                    val projectLine = LineLayer(LAYER_PROJECTS_LINE, SOURCE_PROJECTS)
                    projectLine.setProperties(PropertyFactory.lineColor(Color(0xFF0D47A1).toArgb()), PropertyFactory.lineWidth(2f))
                    style.addLayer(projectLine)
                    
                    scope.launch { updateProjectsLayer(map, expedientes, visibleProjectIds) }
                 }
            }
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView }, 
            modifier = Modifier.fillMaxSize()
        )

        // Cruz central
        if (!isSearching && !showCustomKeyboard) {
            Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.Black.copy(0.5f), modifier = Modifier.size(38.dp))
                Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // --- BUSCADOR (TOP LEFT) ---
        Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp, end = 16.dp).fillMaxWidth(0.65f)) {
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showCustomKeyboard = true
                        isPanelExpanded = false 
                    }
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

        // --- BOTONES (TOP END) ---
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            
            // Botón Capas
            SmallFloatingActionButton(onClick = { showLayerMenu = !showLayerMenu; showProjectsMenu = false }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.Settings, "Capas") }

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

            // Botón Proyectos (Visibilidad KML)
            SmallFloatingActionButton(onClick = { showProjectsMenu = !showProjectsMenu; showLayerMenu = false }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.Folder, "Proyectos") }

            AnimatedVisibility(visible = showProjectsMenu) {
                Card(modifier = Modifier.width(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Proyectos (KML)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (expedientes.isEmpty()) {
                            Text("No hay proyectos cargados", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(4.dp))
                        } else {
                            // Lista Scroleable si hay muchos
                            Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                expedientes.forEach { exp ->
                                    val isVisible = visibleProjectIds.contains(exp.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            visibleProjectIds = if (isVisible) visibleProjectIds - exp.id else visibleProjectIds + exp.id
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isVisible, 
                                            onCheckedChange = { chk -> visibleProjectIds = if (chk) visibleProjectIds + exp.id else visibleProjectIds - exp.id },
                                            modifier = Modifier.size(30.dp).padding(4.dp),
                                            colors = CheckboxDefaults.colors(checkedColor = FieldGreen)
                                        )
                                        Text(exp.titular, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.List, "Proyectos") }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.CameraAlt, "Cámara") }
            SmallFloatingActionButton(onClick = { enableLocation(mapInstance, context, shouldCenter = true) }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.MyLocation, "Ubicación") }
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
                                    style = MaterialTheme.typography.titleMedium, 
                                    fontWeight = FontWeight.Bold, 
                                    color = FieldGreen
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
                                    if (selectedTab == 0) {
                                        if (recintoData != null) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Uso SIGPAC", recintoData!!["uso_sigpac"], Modifier.weight(1f)); AttributeItem("Superficie", "${recintoData!!["superficie"]} ha", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(12.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Pendiente Media", "${recintoData!!["pendiente_media"]}%", Modifier.weight(1f)); AttributeItem("Altitud", "${recintoData!!["altitud"]} m", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(12.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Región", recintoData!!["region"], Modifier.weight(1f)); AttributeItem("Coef. Regadío", "${recintoData!!["coef_regadio"]}%", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(12.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Subvencionabilidad", "${recintoData!!["subvencionabilidad"]}%", Modifier.weight(1f)); AttributeItem("Incidencias", recintoData!!["incidencias"]?.takeIf { it.isNotEmpty() } ?: "Ninguna", Modifier.weight(1f)) }
                                        }
                                    } else {
                                        if (cultivoData != null) {
                                            val c = cultivoData!!
                                            Text("Datos de Expediente", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Núm. Exp", c["exp_num"], Modifier.weight(1f)); AttributeItem("Año", c["exp_ano"], Modifier.weight(1f)) }
                                            Divider(Modifier.padding(vertical=6.dp), color = FieldDivider)
                                            Text("Datos Agrícolas", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Producto", c["parc_producto"], Modifier.weight(1f)); val supCultRaw = c["parc_supcult"]?.toDoubleOrNull() ?: 0.0; val supCultHa = supCultRaw / 10000.0; AttributeItem("Superficie", "${String.format(Locale.US, "%.4f", supCultHa)} ha", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(8.dp))
                                            Row(Modifier.fillMaxWidth()) { val sist = c["parc_sistexp"]; val sistLabel = when(sist) { "S" -> "Secano"; "R" -> "Regadío"; else -> sist }; AttributeItem("Sist. Expl.", sistLabel, Modifier.weight(1f)); AttributeItem("Ind. Cultivo", c["parc_indcultapro"], Modifier.weight(1f)) }
                                            Spacer(Modifier.height(8.dp))
                                            AttributeItem("Tipo Aprovechamiento", c["tipo_aprovecha"], Modifier.fillMaxWidth())
                                            Divider(Modifier.padding(vertical=6.dp), color = FieldDivider)
                                            Text("Ayudas Solicitadas", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                            AttributeItem("Ayudas Parc.", c["parc_ayudasol"], Modifier.fillMaxWidth())
                                            Spacer(Modifier.height(4.dp))
                                            AttributeItem("Ayudas PDR", c["pdr_rec"], Modifier.fillMaxWidth())
                                            Divider(Modifier.padding(vertical=6.dp), color = FieldDivider)
                                            Text("Cultivo Secundario", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=4.dp))
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Producto Sec.", c["cultsecun_producto"], Modifier.weight(1f)); AttributeItem("Ayuda Sec.", c["cultsecun_ayudasol"], Modifier.weight(1f)) }
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
