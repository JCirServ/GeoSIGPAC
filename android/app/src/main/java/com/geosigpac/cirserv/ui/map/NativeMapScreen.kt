
package com.geosigpac.cirserv.ui.map

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.BaseMap
import com.geosigpac.cirserv.ui.DEFAULT_ZOOM
import com.geosigpac.cirserv.ui.LAYER_RECINTO_HIGHLIGHT_FILL
import com.geosigpac.cirserv.ui.LAYER_RECINTO_HIGHLIGHT_LINE
import com.geosigpac.cirserv.ui.SOURCE_SEARCH_RESULT
import com.geosigpac.cirserv.ui.VALENCIA_LAT
import com.geosigpac.cirserv.ui.VALENCIA_LNG
import com.geosigpac.cirserv.ui.computeLocalBoundsAndFeature
import com.geosigpac.cirserv.ui.enableLocation
import com.geosigpac.cirserv.ui.loadMapStyle
import com.geosigpac.cirserv.ui.searchParcelLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

@SuppressLint("MissingPermission")
@Composable
fun NativeMapScreen(
    expedientes: List<NativeExpediente>,
    searchTarget: String?,
    followUserTrigger: Long = 0L, 
    onNavigateToProjects: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    // --- ESTADO MAPA ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(BaseMap.PNOA) }
    var showRecinto by remember { mutableStateOf(true) }
    var showCultivo by remember { mutableStateOf(true) }
    var initialLocationSet by remember { mutableStateOf(false) }

    // --- ESTADO DATOS ---
    var visibleProjectIds by remember { mutableStateOf<Set<String>>(expedientes.map { it.id }.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    
    // Datos Info
    var instantSigpacRef by remember { mutableStateOf("") }
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

    // Inicializar Singleton MapLibre
    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }

    // --- LIFECYCLE MANAGEMENT ---
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
            } catch (e: Exception) { Log.e(TAG_MAP, "Map Lifecycle Error: ${e.message}") }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { mapView.onPause(); mapView.onStop(); mapView.onDestroy() } catch (_: Exception) {}
        }
    }

    // --- FUNCIÓN CLEAR SEARCH ---
    fun clearSearch() {
        searchQuery = ""
        searchActive = false
        lastDataId = null
        recintoData = null
        cultivoData = null
        instantSigpacRef = ""
        mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(Expression.literal(false)) }
        mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(Expression.literal(false)) }
        mapInstance?.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    // --- FUNCIÓN PERFORM SEARCH ---
    fun performSearch() {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        isSearching = true
        searchActive = true

        scope.launch {
            val map = mapInstance ?: return@launch
            
            // 1. BÚSQUEDA LOCAL
            if (searchQuery.startsWith("LOC:")) {
                val localId = searchQuery.substring(4)
                val targetParcel = expedientes.flatMap { it.parcelas }.find { it.id == localId }
                
                if (targetParcel != null) {
                    val localResult = computeLocalBoundsAndFeature(targetParcel)
                    if (localResult != null) {
                        map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(localResult.feature)
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(localResult.bounds, 100), 1000)
                        instantSigpacRef = targetParcel.referencia
                    } else {
                        Toast.makeText(context, "Geometría KML inválida", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Parcela no encontrada localmente", Toast.LENGTH_SHORT).show()
                }
                isSearching = false
                return@launch
            }
            
            // 2. BÚSQUEDA REMOTA (API)
            val parts = searchQuery.split(":").map { it.trim() }
            if (parts.size < 4) {
                Toast.makeText(context, "Formato: Prov:Mun:Pol:Parc[:Rec]", Toast.LENGTH_LONG).show()
                isSearching = false
                return@launch
            }

            val prov = parts[0]; val mun = parts[1]; val pol = parts[2]; val parc = parts[3]; val rec = parts.getOrNull(4)

            // Filtro visual en capa MVT
            if (map.style != null) {
                val filterList = mutableListOf<Expression>(
                    Expression.eq(Expression.toString(Expression.get("provincia")), Expression.literal(prov)),
                    Expression.eq(Expression.toString(Expression.get("municipio")), Expression.literal(mun)),
                    Expression.eq(Expression.toString(Expression.get("poligono")), Expression.literal(pol)),
                    Expression.eq(Expression.toString(Expression.get("parcela")), Expression.literal(parc))
                )
                if (rec != null) filterList.add(Expression.eq(Expression.toString(Expression.get("recinto")), Expression.literal(rec)))
                
                val filter = Expression.all(*filterList.toTypedArray())
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(filter) }
                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(filter) }
            }

            // Búsqueda de Geometría
            val result = searchParcelLocation(prov, mun, pol, parc, rec)
            if (result != null) {
                map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(result.feature)
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(result.bounds, 100), 1500)
            } else {
                Toast.makeText(context, "Ubicación no encontrada en SIGPAC", Toast.LENGTH_SHORT).show()
            }
            isSearching = false
        }
    }

    // --- MAP INITIALIZATION ---
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

            // Listener de Movimiento (Reset búsqueda si usuario mueve mapa)
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    if (searchActive) {
                        searchActive = false
                        map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                        val emptyFilter = Expression.literal(false)
                        map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
                        map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
                    }
                }
            }

            // Realtime Info
            map.addOnCameraMoveListener { 
                if (!searchActive) instantSigpacRef = MapLogic.updateRealtimeInfo(map)
            }

            // Extended Data (CORREGIDO: Usando scope.launch en lugar de bloqueo)
            map.addOnCameraIdleListener {
                if (!searchActive) {
                    apiJob?.cancel()
                    apiJob = scope.launch {
                        isLoadingData = true
                        try {
                            val (r, c) = MapLogic.fetchExtendedData(map)
                            if (r != null) {
                                val uniqueId = "${r["provincia"]}-${r["municipio"]}-${r["poligono"]}-${r["parcela"]}-${r["recinto"]}"
                                if (uniqueId != lastDataId) {
                                    lastDataId = uniqueId
                                    recintoData = r
                                    cultivoData = c
                                    instantSigpacRef = "$uniqueId".replace("-", ":")
                                }
                            } else {
                                lastDataId = null
                                recintoData = null
                                cultivoData = null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG_MAP, "Error fetching idle data: ${e.message}")
                        } finally {
                            isLoadingData = false
                        }
                    }
                }
            }

            // Cargar Estilo Base
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, !initialLocationSet) {
                initialLocationSet = true
            }
            
            // Configurar Capas de Proyectos
            MapLayers.setupProjectLayers(map)
            scope.launch { MapLayers.updateProjectsLayer(map, expedientes, visibleProjectIds) }
        })
    }

    // --- ACTUALIZAR PROYECTOS AL CAMBIAR DATOS ---
    LaunchedEffect(expedientes, visibleProjectIds) {
        mapInstance?.let { map ->
            if (map.style != null && map.style!!.isFullyLoaded) {
                 MapLayers.updateProjectsLayer(map, expedientes, visibleProjectIds)
            }
        }
    }

    // --- TRIGGER BÚSQUEDA EXTERNA ---
    LaunchedEffect(searchTarget, mapInstance) {
        if (!searchTarget.isNullOrEmpty() && mapInstance != null) {
            delay(800)
            searchQuery = searchTarget
            performSearch()
        }
    }

    // --- TRIGGER UBICACIÓN USUARIO ---
    LaunchedEffect(followUserTrigger) {
        if (followUserTrigger > 0 && mapInstance != null) {
            clearSearch()
            enableLocation(mapInstance, context, shouldCenter = true)
        }
    }

    // --- RECARGA ESTILO (CAPAS) ---
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) { }
            MapLayers.setupProjectLayers(map)
            scope.launch { MapLayers.updateProjectsLayer(map, expedientes, visibleProjectIds) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        
        MapOverlay(
            searchQuery = searchQuery,
            isSearching = isSearching,
            isLoadingData = isLoadingData,
            currentBaseMap = currentBaseMap,
            showRecinto = showRecinto,
            showCultivo = showCultivo,
            expedientes = expedientes,
            visibleProjectIds = visibleProjectIds,
            instantSigpacRef = instantSigpacRef,
            recintoData = recintoData,
            cultivoData = cultivoData,
            onSearchQueryChange = { searchQuery = it },
            onSearchPerform = { performSearch() },
            onClearSearch = { clearSearch() },
            onChangeBaseMap = { currentBaseMap = it },
            onToggleRecinto = { showRecinto = it },
            onToggleCultivo = { showCultivo = it },
            onToggleProjectVisibility = { id -> 
                visibleProjectIds = if (visibleProjectIds.contains(id)) visibleProjectIds - id else visibleProjectIds + id 
            },
            onNavigateToProjects = onNavigateToProjects,
            onOpenCamera = onOpenCamera,
            onCenterLocation = { enableLocation(mapInstance, context, shouldCenter = true) }
        )
    }
}
