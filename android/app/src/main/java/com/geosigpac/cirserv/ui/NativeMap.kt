
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Locale

private const val TAG = "GeoSIGPAC_Map"
private const val SOURCE_PROJECT_ID = "project-source"
private const val LAYER_PROJECT_FILL = "project-layer-fill"
private const val LAYER_PROJECT_OUTLINE = "project-layer-outline"
private const val LAYER_PROJECT_SYMBOL = "project-layer-symbol"

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    targetLat: Double?,
    targetLng: Double?,
    expedientes: List<NativeExpediente>,
    onNavigateToProjects: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var initialLocationSet by remember { mutableStateOf(false) }

    // Selector de Proyecto KML
    var selectedProject by remember { mutableStateOf<NativeExpediente?>(null) }
    var showProjectDropdown by remember { mutableStateOf(false) }

    // Búsqueda
    var searchQuery by remember { mutableStateOf("") }
    var showCustomKeyboard by remember { mutableStateOf(false) }

    // Datos Bottom Sheet
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    // Función para renderizar el proyecto KML en el mapa
    fun updateProjectLayer(project: NativeExpediente?) {
        val map = mapInstance ?: return
        val style = map.style ?: return

        style.removeLayer(LAYER_PROJECT_SYMBOL)
        style.removeLayer(LAYER_PROJECT_OUTLINE)
        style.removeLayer(LAYER_PROJECT_FILL)
        style.removeSource(SOURCE_PROJECT_ID)

        if (project == null) return

        val features = project.parcelas.mapNotNull { parcela ->
            val geometry = parcela.geometry
            if (geometry != null && geometry.size >= 3) {
                // Es un polígono
                val points = geometry.map { Point.fromLngLat(it[0], it[1]) }
                val feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
                feature.addStringProperty("referencia", parcela.referencia)
                feature
            } else {
                // Es un punto
                val feature = Feature.fromGeometry(Point.fromLngLat(parcela.lng, parcela.lat))
                feature.addStringProperty("referencia", parcela.referencia)
                feature
            }
        }

        if (features.isEmpty()) return

        val source = GeoJsonSource(SOURCE_PROJECT_ID, FeatureCollection.fromFeatures(features))
        style.addSource(source)

        // Relleno Cian Neón suave
        val fillLayer = FillLayer(LAYER_PROJECT_FILL, SOURCE_PROJECT_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Color(0xFF00E5FF).toArgb()),
                PropertyFactory.fillOpacity(0.25f)
            )
        }
        style.addLayer(fillLayer)

        // Borde Cian Neón intenso
        val outlineLayer = LineLayer(LAYER_PROJECT_OUTLINE, SOURCE_PROJECT_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color(0xFF00E5FF).toArgb()),
                PropertyFactory.lineWidth(2.5f)
            )
        }
        style.addLayer(outlineLayer)

        // Etiqueta
        val symbolLayer = SymbolLayer(LAYER_PROJECT_SYMBOL, SOURCE_PROJECT_ID).apply {
            setProperties(
                PropertyFactory.textField(Expression.get("referencia")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor(Color.White.toArgb()),
                PropertyFactory.textHaloColor(Color.Black.toArgb()),
                PropertyFactory.textHaloWidth(1f),
                PropertyFactory.textOffset(arrayOf(0f, 1f))
            )
        }
        style.addLayer(symbolLayer)

        // Zoom al proyecto
        if (project.parcelas.isNotEmpty()) {
            val p = project.parcelas.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 17.0))
        }
    }

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true
            
            if (!initialLocationSet) {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(VALENCIA_LAT, VALENCIA_LNG)).zoom(DEFAULT_ZOOM).build()
            }

            map.addOnCameraMoveListener { updateHighlightVisuals(map) }
            map.addOnCameraIdleListener { updateDataSheet(map) }

            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
                initialLocationSet = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Mira central
        if (!showCustomKeyboard) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.Add, "Mira", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // --- SELECTOR DE PROYECTO (Top Center) ---
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
            Card(
                modifier = Modifier.widthIn(min = 200.dp).clickable { showProjectDropdown = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F).copy(alpha = 0.9f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedProject?.titular ?: "Seleccionar KML",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                }
            }

            DropdownMenu(
                expanded = showProjectDropdown,
                onDismissRequest = { showProjectDropdown = false },
                modifier = Modifier.background(Color(0xFF13141F)).widthIn(min = 200.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Ocultar Proyectos", color = Color.Gray, fontSize = 12.sp) },
                    onClick = { 
                        selectedProject = null
                        showProjectDropdown = false
                        updateProjectLayer(null)
                    }
                )
                expedientes.forEach { exp ->
                    DropdownMenuItem(
                        text = { Text(exp.titular, color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            selectedProject = exp
                            showProjectDropdown = false
                            updateProjectLayer(exp)
                        }
                    )
                }
            }
        }

        // --- BUSCADOR (Top Left) ---
        Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp)) {
            IconButton(
                onClick = { showCustomKeyboard = true },
                modifier = Modifier.size(48.dp).background(Color(0xFF13141F).copy(alpha = 0.8f), CircleShape).border(1.dp, Color.White.copy(0.1f), CircleShape)
            ) { Icon(Icons.Default.Search, "Buscar", tint = Color(0xFF00FF88)) }
        }

        // --- BOTONES LATERALES ---
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(onClick = { showLayerMenu = !showLayerMenu }, containerColor = Color(0xFF13141F), contentColor = Color(0xFF00FF88), shape = CircleShape) { Icon(Icons.Default.Layers, "Capas") }
            
            AnimatedVisibility(visible = showLayerMenu) {
                Card(modifier = Modifier.width(180.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Mapa Base", fontSize = 10.sp, color = Color.Gray)
                        BaseMap.values().forEach { base ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { currentBaseMap = base }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentBaseMap == base, onClick = { currentBaseMap = base })
                                Text(base.title, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            
            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = Color(0xFF13141F), contentColor = Color(0xFF00FF88)) { Icon(Icons.Default.List, null) }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = Color(0xFF13141F), contentColor = Color(0xFF00FF88)) { Icon(Icons.Default.PhotoCamera, null) }
            SmallFloatingActionButton(onClick = { enableLocation(mapInstance, context, true) }, containerColor = Color(0xFF00FF88), contentColor = Color.Black) { Icon(Icons.Default.MyLocation, null) }
        }

        // BOTTOM SHEET (Info Recinto)
        AnimatedVisibility(visible = recintoData != null && !showCustomKeyboard, enter = slideInVertically(initialOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> isPanelExpanded = drag < 0 } },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E1A)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("DATOS SIGPAC", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text(recintoData!!["referencia"] ?: "-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { recintoData = null }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                    }
                    if (isPanelExpanded) {
                        Spacer(Modifier.height(16.dp))
                        Row { AttributeItem("Uso", recintoData!!["uso_sigpac"], Modifier.weight(1f)); AttributeItem("Superficie", "${recintoData!!["superficie"]} ha", Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text("TOMAR FOTO")
                    }
                }
            }
        }

        // Teclado
        AnimatedVisibility(visible = showCustomKeyboard, enter = slideInVertically(initialOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            CustomSigpacKeyboard(
                onKey = { searchQuery += it },
                onBackspace = { if(searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                onSearch = { showCustomKeyboard = false },
                onClose = { showCustomKeyboard = false }
            )
        }
    }
}

fun updateHighlightVisuals(map: MapLibreMap) { /* Lógica de detección SIGPAC */ }
fun updateDataSheet(map: MapLibreMap) { /* Lógica de extracción de datos */ }
