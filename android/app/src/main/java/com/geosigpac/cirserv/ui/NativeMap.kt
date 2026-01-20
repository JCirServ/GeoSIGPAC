
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
import com.geosigpac.cirserv.model.NativeParcela
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
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

private const val TAG = "GeoSIGPAC_Map"

@SuppressLint("MissingPermission")
@Composable
fun NativeMap(
    targetLat: Double?,
    targetLng: Double?,
    kmlParcelas: List<NativeParcela>,
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
    var showLayerMenu by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showCustomKeyboard by remember { mutableStateOf(false) }

    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var selectedParcelaId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

    remember { MapLibre.getInstance(context) }
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    // Gestión de Ciclo de Vida
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

    // Lógica para cargar datos al seleccionar parcela
    fun selectParcela(parcela: NativeParcela) {
        selectedParcelaId = parcela.id
        isPanelExpanded = true
        isLoadingData = true
        
        // Datos básicos inmediatos del KML
        recintoData = mapOf(
            "provincia" to parcela.referencia.split(":").getOrNull(0).orEmpty(),
            "municipio" to parcela.referencia.split(":").getOrNull(1).orEmpty(),
            "poligono" to parcela.referencia.split(":").getOrNull(4).orEmpty(),
            "parcela" to parcela.referencia.split(":").getOrNull(5).orEmpty(),
            "recinto" to parcela.referencia.split(":").getOrNull(6).orEmpty(),
            "superficie" to parcela.area.toString(),
            "uso_sigpac" to parcela.uso
        )

        // Hidratación OGC
        apiJob?.cancel()
        apiJob = scope.launch {
            val fullData = fetchFullSigpacInfo(parcela.lat, parcela.lng)
            if (fullData != null) recintoData = fullData
            isLoadingData = false
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isLogoEnabled = false
            
            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val rect = RectF(screenPoint.x - 10, screenPoint.y - 10, screenPoint.x + 10, screenPoint.y + 10)
                
                // Prioridad 1: Detección en capa KML Local
                val kmlFeatures = map.queryRenderedFeatures(rect, "kml-layer-fill")
                if (kmlFeatures.isNotEmpty()) {
                    val id = kmlFeatures[0].getStringProperty("id")
                    kmlParcelas.find { it.id == id }?.let { selectParcela(it) }
                    return@addOnMapClickListener true
                }
                
                // Prioridad 2: Detección en capas oficiales
                val sigpacFeatures = map.queryRenderedFeatures(rect, LAYER_RECINTO_FILL)
                if (sigpacFeatures.isNotEmpty()) {
                    // Lógica existente de detección oficial...
                }
                false
            }

            loadMapStyle(map, currentBaseMap, showRecinto, false, context, true) {
                // Style loaded callback
                updateKmlSource(map, kmlParcelas)
            }
        }
    }

    // Actualizar geometrías KML si cambian las parcelas
    LaunchedEffect(kmlParcelas) {
        mapInstance?.let { updateKmlSource(it, kmlParcelas) }
    }

    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 18.0), 1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Buscador y Botones (Misma UI que antes)
        Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(0.65f)) {
            TextField(
                value = searchQuery, onValueChange = {}, readOnly = true,
                placeholder = { Text("Buscar parcela...", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().clickable { showCustomKeyboard = true },
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = FieldBackground.copy(0.9f), unfocusedContainerColor = FieldBackground.copy(0.7f)),
                trailingIcon = { Icon(Icons.Default.Search, null, tint = FieldGreen) }
            )
        }

        // Botonera Lateral
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = Color.White, contentColor = Color(0xFF006D3E), shape = CircleShape) { Icon(Icons.Default.List, null) }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = Color.White, contentColor = Color(0xFF006D3E), shape = CircleShape) { Icon(Icons.Default.CameraAlt, null) }
            SmallFloatingActionButton(onClick = { enableLocation(mapInstance, context, true) }, containerColor = Color(0xFF006D3E), contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.MyLocation, null) }
        }

        // Bottom Sheet de Información
        AnimatedVisibility(visible = recintoData != null, enter = slideInVertically(initialOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -20) isPanelExpanded = true else if (dragAmount > 20) isPanelExpanded = false } },
                colors = CardDefaults.cardColors(containerColor = FieldBackground.copy(0.98f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("INSPECCIÓN KML", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text(recintoData?.get("referencia") ?: "Recinto", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { recintoData = null }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                    }
                    if (isPanelExpanded) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.1f))
                        Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                            recintoData?.forEach { (k, v) -> AttributeItem(k.uppercase(), v) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convierte las parcelas KML a un Source de GeoJSON y lo inyecta en el estilo.
 */
private fun updateKmlSource(map: MapLibreMap, parcelas: List<NativeParcela>) {
    val style = map.style ?: return
    val features = parcelas.filter { it.geometryWkt != null }.joinToString(",") { p ->
        """{
            "type": "Feature",
            "properties": { "id": "${p.id}", "referencia": "${p.referencia}" },
            "geometry": ${wktToJson(p.geometryWkt!!)}
        }"""
    }
    val geoJson = """{ "type": "FeatureCollection", "features": [$features] }"""
    
    style.getSourceAs<GeoJsonSource>("kml-source")?.setGeoJson(geoJson) ?: run {
        style.addSource(GeoJsonSource("kml-source", geoJson))
        
        // Capa de relleno Cyan para selección
        val fill = FillLayer("kml-layer-fill", "kml-source")
        fill.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.fillColor(Color(0xFF22D3EE).toArgb()),
            org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.2f)
        )
        style.addLayer(fill)

        // Línea Cyan Neón gruesa
        val line = LineLayer("kml-layer-line", "kml-source")
        line.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor(Color(0xFF22D3EE).toArgb()),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(3.5f)
        )
        style.addLayer(line)
    }
}

/**
 * Parser ultra-ligero de WKT POLYGON a JSON Geometry.
 */
private fun wktToJson(wkt: String): String {
    val coords = wkt.replace("POLYGON((", "").replace("))", "")
        .split(",")
        .map { it.trim().split(" ") }
        .joinToString(",") { "[${it[0]}, ${it[1]}]" }
    return """{ "type": "Polygon", "coordinates": [[$coords]] }"""
}
