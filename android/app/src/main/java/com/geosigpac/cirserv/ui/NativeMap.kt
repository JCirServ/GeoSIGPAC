
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.data.AppDatabase
import com.geosigpac.cirserv.data.RecintoEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

// IDs para fuentes GeoJSON dinámicas
const val SOURCE_DECLARED = "source-declared-geojson"
const val LAYER_DECLARED_FILL = "layer-declared-fill"
const val LAYER_DECLARED_LINE = "layer-declared-line"

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
    val dao = remember { AppDatabase.getInstance(context).inspectionDao() }

    // --- ESTADO MAPA ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    
    // --- ESTADO DATOS ---
    // Cargamos todos los recintos declarados de la DB para mostrarlos en el mapa
    val declaredRecintos by dao.getAllExpedientes().collectAsState(initial = emptyList())
    var activeRecintosList by remember { mutableStateOf<List<RecintoEntity>>(emptyList()) }
    
    // Selección Actual
    var selectedRecinto by remember { mutableStateOf<RecintoEntity?>(null) }
    var sigpacData by remember { mutableStateOf<Map<String, String>?>(null) }
    
    // UI Estado
    var isPanelExpanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Comparativa, 1: Oficial, 2: Declarado
    
    // Carga inicial de recintos (si hay expedientes)
    LaunchedEffect(declaredRecintos) {
        if (declaredRecintos.isNotEmpty()) {
            // Por simplicidad, cargamos los recintos del último expediente importado
            val lastExp = declaredRecintos.first()
            activeRecintosList = dao.getRecintosList(lastExp.id)
            updateDeclaredLayer(mapInstance, activeRecintosList)
        }
    }

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
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // --- MANEJO DE CLICS EN MAPA ---
    fun handleMapClick(map: MapLibreMap, point: android.graphics.PointF) {
        val latLng = map.projection.fromScreenLocation(point)
        
        // 1. Buscar en capa GeoJSON (Declarado)
        // Como es GeoJSON local, podemos buscar en memoria o con queryRenderedFeatures si es poly
        // Simplificación: Buscar por distancia mínima al punto en la lista local
        val clickedDeclared = activeRecintosList.minByOrNull { 
            val json = JsonParser.parseString(it.geomDeclaradaJson).asJsonObject
            val coords = json.getAsJsonObject("geometry").getAsJsonArray("coordinates")
            // Distancia euclídea simple (solo válida para demo/puntos cercanos)
            val rLat = coords[1].asDouble
            val rLng = coords[0].asDouble
            val d = Math.pow(rLat - latLng.latitude, 2.0) + Math.pow(rLng - latLng.longitude, 2.0)
            d
        }
        
        // Umbral de clic (aprox)
        val isDeclaredHit = clickedDeclared != null // En app real, comprobar distancia < X metros

        scope.launch {
            // 2. Buscar datos Oficiales SIGPAC (WFS/API)
            val oficialInfo = fetchFullSigpacInfo(latLng.latitude, latLng.longitude)
            
            if (oficialInfo != null || isDeclaredHit) {
                selectedRecinto = clickedDeclared
                sigpacData = oficialInfo
                activeTab = 0
            } else {
                selectedRecinto = null
                sigpacData = null
            }
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapInstance = map
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true

            // Cargar Estilo Base
            loadMapStyle(map, currentBaseMap, true, true, context, true) {}

            // Añadir Fuente para "Declarado" (GeoJSON vacío inicial)
            map.getStyle { style ->
                if (style.getSource(SOURCE_DECLARED) == null) {
                    style.addSource(GeoJsonSource(SOURCE_DECLARED))
                    
                    // Capa Relleno Azul Transparente (Declarado)
                    val fill = FillLayer(LAYER_DECLARED_FILL, SOURCE_DECLARED)
                    fill.setProperties(
                        PropertyFactory.fillColor(Color(0xFF2196F3).toArgb()), // Azul
                        PropertyFactory.fillOpacity(0.4f)
                    )
                    style.addLayer(fill)

                    // Capa Borde Azul (Declarado)
                    val line = LineLayer(LAYER_DECLARED_LINE, SOURCE_DECLARED)
                    line.setProperties(
                        PropertyFactory.lineColor(Color(0xFF1565C0).toArgb()),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f)) // Punteado
                    )
                    style.addLayer(line)
                }
            }

            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                handleMapClick(map, screenPoint)
                true
            }
            
            // Si ya teníamos datos cargados, actualizamos
            if (activeRecintosList.isNotEmpty()) {
                updateDeclaredLayer(map, activeRecintosList)
            }
        }
    }

    // Efecto de enfoque desde WebView
    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(targetLat, targetLng), 17.0), 1500)
            // Simular clic para cargar datos
            scope.launch {
                val oficial = fetchFullSigpacInfo(targetLat, targetLng)
                sigpacData = oficial
            }
        }
    }

    // --- RENDER UI ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        
        // Botones flotantes (mantener los existentes)
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = FieldSurface, contentColor = FieldGreen) { Icon(Icons.Default.List, "Lista") }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = FieldSurface, contentColor = FieldGreen) { Icon(Icons.Default.CameraAlt, "Foto") }
        }

        // PANEL DE INSPECCIÓN (BottomSheet)
        AnimatedVisibility(
            visible = selectedRecinto != null || sigpacData != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            InspectionBottomSheet(
                recinto = selectedRecinto,
                sigpac = sigpacData,
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                onClose = { selectedRecinto = null; sigpacData = null },
                onDiscrepancy = { type ->
                    scope.launch {
                        selectedRecinto?.let { r ->
                            dao.updateRecintoStatus(r.id, "discrepancia", type)
                            Toast.makeText(context, "Discrepancia '$type' registrada", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onValidate = {
                    scope.launch {
                        selectedRecinto?.let { r ->
                            dao.updateRecintoStatus(r.id, "conforme", null)
                            Toast.makeText(context, "Recinto Validado Correctamente", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// Función auxiliar para actualizar la capa GeoJSON
fun updateDeclaredLayer(map: MapLibreMap?, recintos: List<RecintoEntity>) {
    map?.getStyle { style ->
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_DECLARED)
        if (source != null) {
            // Construir FeatureCollection
            val features = recintos.map { it.geomDeclaradaJson }.joinToString(",")
            val geoJson = """
                {
                    "type": "FeatureCollection",
                    "features": [$features]
                }
            """.trimIndent()
            source.setGeoJson(geoJson)
        }
    }
}

@Composable
fun InspectionBottomSheet(
    recinto: RecintoEntity?,
    sigpac: Map<String, String>?,
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    onClose: () -> Unit,
    onDiscrepancy: (String) -> Unit,
    onValidate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(380.dp),
        colors = CardDefaults.cardColors(containerColor = FieldBackground.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("INSPECCIÓN DE CAMPO", style = MaterialTheme.typography.labelSmall, color = FieldGray)
                    val ref = if (sigpac != null) 
                        "${sigpac["poligono"]}-${sigpac["parcela"]}-${sigpac["recinto"]}" 
                        else "Declarado: ${recinto?.parcela ?: "?"}"
                    Text("Ref: $ref", style = MaterialTheme.typography.titleMedium, color = FieldGreen, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }

            // Pestañas
            TabRow(selectedTabIndex = activeTab, containerColor = Color.Transparent, contentColor = FieldGreen) {
                Tab(selected = activeTab == 0, onClick = { onTabChange(0) }, text = { Text("COMPARATIVA") })
                Tab(selected = activeTab == 1, onClick = { onTabChange(1) }, text = { Text("SIGPAC") })
                Tab(selected = activeTab == 2, onClick = { onTabChange(2) }, text = { Text("DECLARADO") })
            }
            
            Divider(color = FieldDivider)

            // Contenido
            Column(modifier = Modifier.weight(1f).padding(16.dp).verticalScroll(rememberScrollState())) {
                when (activeTab) {
                    0 -> { // COMPARATIVA
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("OFICIAL (SIGPAC)", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Uso: ${sigpac?.get("uso_sigpac") ?: "N/D"}", color = FieldGray)
                                Text("Sup: ${sigpac?.get("superficie") ?: "-"} ha", color = FieldGray)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("DECLARADO", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                                Text("Uso: ${recinto?.usoDeclarado ?: "N/D"}", color = FieldGray)
                                Text("Sup: ${recinto?.superficieDeclarada ?: "-"} ha", color = FieldGray)
                            }
                        }
                        
                        Spacer(Modifier.height(20.dp))
                        Text("Acciones de Inspección", style = MaterialTheme.typography.labelSmall, color = FieldGray)
                        Spacer(Modifier.height(8.dp))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onValidate, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = FieldGreen)) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("CONFORME")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onDiscrepancy("uso") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                                Text("Disc. USO", fontSize = 12.sp)
                            }
                            Button(onClick = { onDiscrepancy("geometria") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
                                Text("Disc. LINDE", fontSize = 12.sp)
                            }
                        }
                    }
                    1 -> { // SIGPAC DETALLE
                         if (sigpac != null) {
                             AttributeItem("Región", sigpac["region"], Modifier.fillMaxWidth())
                             AttributeItem("Coef. Regadío", "${sigpac["coef_regadio"]}%", Modifier.fillMaxWidth())
                             AttributeItem("Pendiente", "${sigpac["pendiente_media"]}%", Modifier.fillMaxWidth())
                             AttributeItem("Incidencias", sigpac["incidencias"], Modifier.fillMaxWidth())
                         } else {
                             Text("No hay datos oficiales cargados en este punto.", color = FieldGray)
                         }
                    }
                    2 -> { // DECLARADO DETALLE
                        if (recinto != null) {
                            AttributeItem("Expediente ID", recinto.expedienteId, Modifier.fillMaxWidth())
                            AttributeItem("Geometría", "Punto/Polígono cargado", Modifier.fillMaxWidth())
                            AttributeItem("Estado Actual", recinto.estadoInspeccion.uppercase(), Modifier.fillMaxWidth())
                        } else {
                            Text("Este punto no coincide con ninguna parcela declarada.", color = FieldGray)
                        }
                    }
                }
            }
        }
    }
}
