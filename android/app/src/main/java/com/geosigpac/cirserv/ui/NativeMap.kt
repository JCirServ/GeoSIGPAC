
package com.geosigpac.cirserv.ui

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.model.NativeParcela
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource

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
    val scope = rememberCoroutineScope()
    
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var selectedParcelaId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

    remember { MapLibre.getInstance(context) }
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

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

    fun selectParcela(parcela: NativeParcela) {
        selectedParcelaId = parcela.id
        isPanelExpanded = false
        isLoadingData = true
        
        recintoData = mapOf(
            "referencia" to parcela.referencia,
            "superficie" to String.format("%.4f ha", parcela.area),
            "uso_sigpac" to parcela.uso
        )

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
                val rect = RectF(screenPoint.x - 20, screenPoint.y - 20, screenPoint.x + 20, screenPoint.y + 20)
                
                val kmlFeatures = map.queryRenderedFeatures(rect, "kml-layer-fill")
                if (kmlFeatures.isNotEmpty()) {
                    val id = kmlFeatures[0].getStringProperty("id")
                    kmlParcelas.find { it.id == id }?.let { selectParcela(it) }
                    return@addOnMapClickListener true
                }
                false
            }

            val shouldTracking = targetLat == null
            loadMapStyle(map, currentBaseMap, showRecinto, false, context, shouldTracking) {
                updateKmlSource(map, kmlParcelas)
            }
        }
    }

    LaunchedEffect(mapInstance, targetLat, targetLng) {
        val map = mapInstance ?: return@LaunchedEffect
        if (targetLat != null && targetLng != null) {
            map.locationComponent.cameraMode = CameraMode.NONE
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 18.0), 1000)
        } else {
            enableLocation(map, context, true)
        }
    }

    LaunchedEffect(kmlParcelas) {
        mapInstance?.let { updateKmlSource(it, kmlParcelas) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Botonera Lateral (Muy importante ahora que no hay barra inferior en el mapa)
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Botón para volver a la lista de proyectos
            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = Color.White, contentColor = Color(0xFF006D3E), shape = CircleShape) { 
                Icon(Icons.Default.List, "Volver a Proyectos") 
            }
            // Botón de cámara directa
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = Color.White, contentColor = Color(0xFF006D3E), shape = CircleShape) { 
                Icon(Icons.Default.CameraAlt, "Abrir Cámara") 
            }
            // Botón de mi ubicación
            SmallFloatingActionButton(onClick = { enableLocation(mapInstance, context, true) }, containerColor = Color(0xFF006D3E), contentColor = Color.White, shape = CircleShape) { 
                Icon(Icons.Default.MyLocation, "Mi Posición") 
            }
        }

        // Panel de Información
        AnimatedVisibility(
            visible = recintoData != null, 
            enter = slideInVertically(initialOffsetY = { it }), 
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -20) isPanelExpanded = true else if (dragAmount > 20) isPanelExpanded = false } },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F).copy(0.95f)),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("RECINTO SELECCIONADO", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(recintoData?.get("referencia") ?: "Cargando...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { recintoData = null }, modifier = Modifier.background(Color.White.copy(0.05f), CircleShape)) { 
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp)) 
                        }
                    }
                    
                    if (isPanelExpanded || isLoadingData) {
                        Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(0.1f))
                        if (isLoadingData) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF22D3EE), strokeWidth = 2.dp)
                            }
                        } else {
                            Column(modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AttributeItem("SUPERFICIE", recintoData?.get("superficie"))
                                        AttributeItem("USO SIGPAC", recintoData?.get("uso_sigpac"))
                                        AttributeItem("PENDIENTE", recintoData?.get("pendiente_media")?.let { "$it %" })
                                        AttributeItem("ALTITUD", recintoData?.get("altitud")?.let { "$it m" })
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        AttributeItem("ADMISIBILIDAD", recintoData?.get("subvencionabilidad"))
                                        AttributeItem("REGION", recintoData?.get("region"))
                                        AttributeItem("REGADÍO", recintoData?.get("coef_regadio"))
                                    }
                                }
                                if (recintoData?.get("incidencias")?.isNotEmpty() == true) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("INCIDENCIAS", color = Color.Red.copy(0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    Text(recintoData!!["incidencias"]!!, color = Color.White.copy(0.8f), fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Desliza para ver detalles técnicos", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

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
        
        val fill = FillLayer("kml-layer-fill", "kml-source")
        fill.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.fillColor(Color(0xFF22D3EE).toArgb()),
            org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.15f)
        )
        style.addLayer(fill)

        val line = LineLayer("kml-layer-line", "kml-source")
        line.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.lineColor(Color(0xFF22D3EE).toArgb()),
            org.maplibre.android.style.layers.PropertyFactory.lineWidth(4f)
        )
        style.addLayer(line)
    }
}

private fun wktToJson(wkt: String): String {
    val coords = wkt.replace("POLYGON((", "").replace("))", "")
        .split(",")
        .map { it.trim().split(" ") }
        .joinToString(",") { "[${it[0]}, ${it[1]}]" }
    return """{ "type": "Polygon", "coordinates": [[$coords]] }"""
}
