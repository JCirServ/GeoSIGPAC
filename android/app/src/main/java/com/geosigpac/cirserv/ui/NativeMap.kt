
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

private const val TAG = "GeoSIGPAC_LOG_Map"

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
    
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var initialLocationSet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var showCustomKeyboard by remember { mutableStateOf(false) }
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Log.d(TAG, "Renderizando componente NativeMap")

    remember { 
        Log.d(TAG, "Iniciando MapLibre Instance")
        MapLibre.getInstance(context) 
    }

    val mapView = remember {
        Log.d(TAG, "Creando instancia de MapView")
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d(TAG, "MapView Lifecycle Event: $event")
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
            Log.d(TAG, "Removiendo observador de ciclo de vida del mapa")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun updateHighlightVisuals(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13) return
        
        val center = map.cameraPosition.target ?: return
        val screenPoint = map.projection.toScreenLocation(center)
        val searchArea = RectF(screenPoint.x - 10f, screenPoint.y - 10f, screenPoint.x + 10f, screenPoint.y + 10f)
        
        try {
            val features = map.queryRenderedFeatures(searchArea, LAYER_RECINTO_FILL)
            if (features.isNotEmpty()) {
                val props = features[0].properties()
                Log.d(TAG, "Feature detectada bajo el puntero: $props")
                // Lógica de resaltado...
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando features para resaltado: ${e.message}")
        }
    }

    fun updateDataSheet(map: MapLibreMap) {
        if (searchActive) return
        if (map.cameraPosition.zoom < 13) return
        Log.d(TAG, "Cámara detenida. Actualizando datos de la hoja.")
        // Lógica de actualización de datos...
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "Iniciando getMapAsync")
        mapView.getMapAsync { map ->
            Log.i(TAG, "Callback de MapLibreMap recibido correctamente")
            mapInstance = map
            
            map.addOnCameraMoveListener { updateHighlightVisuals(map) }
            map.addOnCameraIdleListener { updateDataSheet(map) }

            Log.d(TAG, "Cargando estilo inicial...")
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
                Log.i(TAG, "Estilo de mapa cargado y ubicación habilitada")
                initialLocationSet = true
            }
        }
    }

    // --- UI RENDER ---
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { 
            Log.d(TAG, "AndroidView factory ejecutado")
            mapView 
        }, modifier = Modifier.fillMaxSize())

        if (!isSearching && !showCustomKeyboard) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        
        // El resto de la UI (Buscador, Botones, etc.)
        // ... (Mantenido igual para brevedad, pero con logs internos si fuera necesario)
    }
}
