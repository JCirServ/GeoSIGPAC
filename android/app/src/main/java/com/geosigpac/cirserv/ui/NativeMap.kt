
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Locale

private const val TAG = "GeoSIGPAC_Map"
private const val SOURCE_KML_ID = "kml-project-source"
private const val LAYER_KML_FILL = "kml-project-layer-fill"
private const val LAYER_KML_OUTLINE = "kml-project-layer-outline"

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
    val primaryColor = MaterialTheme.colorScheme.primary
    
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var initialLocationSet by remember { mutableStateOf(false) }

    // Selector de Proyecto KML
    var selectedProject by remember { mutableStateOf<NativeExpediente?>(null) }
    var showProjectDropdown by remember { mutableStateOf(false) }

    // Búsqueda e Info
    var searchQuery by remember { mutableStateOf("") }
    var showCustomKeyboard by remember { mutableStateOf(false) }
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    // Lógica para renderizar el proyecto seleccionado en el mapa
    fun updateKmlLayer(project: NativeExpediente?) {
        val map = mapInstance ?: return
        val style = map.style ?: return

        // Limpiar capas anteriores
        if (style.getLayer(LAYER_KML_OUTLINE) != null) style.removeLayer(LAYER_KML_OUTLINE)
        if (style.getLayer(LAYER_KML_FILL) != null) style.removeLayer(LAYER_KML_FILL)
        if (style.getSource(SOURCE_KML_ID) != null) style.removeSource(SOURCE_KML_ID)

        if (project == null || project.parcelas.isEmpty()) return

        val features = project.parcelas.mapNotNull { parcela ->
            parcela.geometry?.let { coords ->
                if (coords.size >= 3) {
                    val points = coords.map { Point.fromLngLat(it[0], it[1]) }
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
                } else null
            } ?: Feature.fromGeometry(Point.fromLngLat(parcela.lng, parcela.lat))
        }

        if (features.isEmpty()) return

        val source = GeoJsonSource(SOURCE_KML_ID, FeatureCollection.fromFeatures(features))
        style.addSource(source)

        // Capa de Relleno: Azul Translúcido
        val fillLayer = FillLayer(LAYER_KML_FILL, SOURCE_KML_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), // Azul Material Design
                PropertyFactory.fillOpacity(0.4f)
            )
        }
        style.addLayer(fillLayer)

        // Capa de Borde: Azul Intenso
        val outlineLayer = LineLayer(LAYER_KML_OUTLINE, SOURCE_KML_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color(0xFF1976D2).toArgb()),
                PropertyFactory.lineWidth(3.0f)
            )
        }
        style.addLayer(outlineLayer)

        // Centrar mapa en el BBox del proyecto
        val boundsBuilder = LatLngBounds.Builder()
        var hasValidCoords = false
        project.parcelas.forEach { p ->
            boundsBuilder.include(LatLng(p.lat, p.lng))
            hasValidCoords = true
            p.geometry?.forEach { boundsBuilder.include(LatLng(it[1], it[0])) }
        }
        
        if (hasValidCoords) {
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1200)
            } catch (e: Exception) {
                if (project.parcelas.isNotEmpty()) {
                     val p = project.parcelas.first()
                     map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 16.5))
                }
            }
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
            
            if (targetLat != null && targetLng != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 16.0))
                initialLocationSet = true
            }

            // Lógica de Resaltado Visual al mover la cámara
            map.addOnCameraMoveListener {
                val screenPoint = map.projection.toScreenLocation(map.cameraPosition.target)
                val features = map.queryRenderedFeatures(screenPoint, LAYER_RECINTO_FILL)
                
                val style = map.style
                if (style != null) {
                    if (features.isNotEmpty()) {
                        val f = features[0]
                        val prov = f.getStringProperty("provincia")
                        val mun = f.getStringProperty("municipio")
                        val pol = f.getStringProperty("poligono")
                        val parc = f.getStringProperty("parcela")
                        val rec = f.getStringProperty("recinto")
                        
                        if (prov != null && mun != null) {
                            val filter = Expression.all(
                                Expression.eq(Expression.get("provincia"), Expression.literal(prov)),
                                Expression.eq(Expression.get("municipio"), Expression.literal(mun)),
                                Expression.eq(Expression.get("poligono"), Expression.literal(pol)),
                                Expression.eq(Expression.get("parcela"), Expression.literal(parc)),
                                Expression.eq(Expression.get("recinto"), Expression.literal(rec))
                            )
                            style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.setFilter(filter)
                            style.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.setFilter(filter)
                        }
                    } else {
                        val emptyFilter = Expression.literal(false)
                        style.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.setFilter(emptyFilter)
                        style.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.setFilter(emptyFilter)
                    }
                }
            }

            // Lógica de Consulta de Datos (Popup) al detener la cámara
            map.addOnCameraIdleListener {
                val center = map.cameraPosition.target
                apiJob?.cancel()
                apiJob = scope.launch {
                    delay(150) // Debounce para no saturar
                    try {
                        val data = fetchFullSigpacInfo(center.latitude, center.longitude)
                        recintoData = data
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

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

        // --- BUSCADOR Y SELECTOR DE PROYECTO ---
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Buscador Original
            Card(
                modifier = Modifier.fillMaxWidth(0.75f).height(50.dp).clickable { showCustomKeyboard = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F).copy(alpha = 0.9f)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(searchQuery.ifEmpty { "Prov:Mun:Pol:Parc" }, color = Color.Gray, fontSize = 13.sp)
                }
            }

            // SELECTOR DE PROYECTO KML
            Box {
                Card(
                    modifier = Modifier.widthIn(min = 220.dp).clickable { showProjectDropdown = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F).copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if(selectedProject != null) Color(0xFF2196F3) else Color.White.copy(0.1f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, null, tint = if(selectedProject != null) Color(0xFF2196F3) else Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selectedProject?.titular ?: "Capa Proyecto KML",
                            color = if(selectedProject != null) Color.White else Color.Gray,
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
                    modifier = Modifier.background(Color(0xFF13141F)).widthIn(min = 220.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Ocultar Capas Proyecto", color = Color.Gray, fontSize = 12.sp) },
                        onClick = { 
                            selectedProject = null
                            showProjectDropdown = false
                            updateKmlLayer(null)
                        }
                    )
                    expedientes.forEach { exp ->
                        DropdownMenuItem(
                            text = { Text(exp.titular, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                selectedProject = exp
                                showProjectDropdown = false
                                updateKmlLayer(exp)
                            }
                        )
                    }
                }
            }
        }

        // --- PANEL DE INFORMACIÓN (Bottom Sheet SIGPAC) ---
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
                            Text("DATOS SIGPAC", color = primaryColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text(recintoData!!["referencia"] ?: "-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { recintoData = null }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                    }
                    if (isPanelExpanded) {
                        Spacer(Modifier.height(16.dp))
                        Row { 
                            AttributeItem("Uso SIGPAC", recintoData!!["uso_sigpac"], Modifier.weight(1f))
                            AttributeItem("Superficie", "${recintoData!!["superficie"]} ha", Modifier.weight(1f)) 
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text("FOTO GEORREFERENCIADA")
                    }
                }
            }
        }

        // Teclado
        AnimatedVisibility(visible = showCustomKeyboard, enter = slideInVertically(initialOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            CustomSigpacKeyboard(
                onKey = { searchQuery += it },
                onBackspace = { if(searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1) },
                onSearch = {
                    showCustomKeyboard = false
                    if (searchQuery.isNotBlank()) {
                        scope.launch {
                            try {
                                val cleanQuery = searchQuery.replace("-", ":").replace(" ", "")
                                val parts = cleanQuery.split(":").filter { it.isNotEmpty() }
                                
                                if (parts.size >= 4) {
                                    val prov = parts[0]
                                    val mun = parts[1]
                                    val pol = parts[2]
                                    val parc = parts[3]
                                    val rec = if (parts.size >= 5) parts[4] else null

                                    Toast.makeText(context, "Localizando ${parts.joinToString(":")}...", Toast.LENGTH_SHORT).show()
                                    
                                    val bounds = searchParcelLocation(prov, mun, pol, parc, rec)
                                    
                                    if (bounds != null) {
                                        mapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 2000)
                                    } else {
                                        Toast.makeText(context, "No se encontró geometría", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Formato: Prov:Mun:Pol:Parc[:Rec]", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error en búsqueda", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onClose = { showCustomKeyboard = false }
            )
        }
    }
}
