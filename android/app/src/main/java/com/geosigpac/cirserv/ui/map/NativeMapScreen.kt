
package com.geosigpac.cirserv.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.ui.BaseMap
import com.geosigpac.cirserv.ui.DEFAULT_ZOOM
import com.geosigpac.cirserv.ui.FullScreenPhotoGallery
import com.geosigpac.cirserv.ui.LAYER_RECINTO_HIGHLIGHT_FILL
import com.geosigpac.cirserv.ui.LAYER_RECINTO_HIGHLIGHT_LINE
import com.geosigpac.cirserv.ui.SOURCE_SEARCH_RESULT
import com.geosigpac.cirserv.ui.VALENCIA_LAT
import com.geosigpac.cirserv.ui.VALENCIA_LNG
import com.geosigpac.cirserv.ui.computeLocalBoundsAndFeature
import com.geosigpac.cirserv.ui.enableLocation
import com.geosigpac.cirserv.ui.loadMapStyle
import com.geosigpac.cirserv.ui.searchParcelLocation
import com.geosigpac.cirserv.utils.MapSettings
import com.geosigpac.cirserv.utils.MapSettingsStorage
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
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
    onOpenCamera: () -> Unit,
    onUpdateExpedientes: (List<NativeExpediente>) -> Unit // Nuevo callback para borrado de fotos
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    // --- CARGAR CONFIGURACIÓN PERSISTENTE ---
    val initialSettings = remember { MapSettingsStorage.loadSettings(context) }

    // --- ESTADO MAPA ---
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentBaseMap by remember { mutableStateOf(initialSettings.getBaseMapEnum()) }
    var showRecinto by remember { mutableStateOf(initialSettings.showRecinto) }
    var showCultivo by remember { mutableStateOf(initialSettings.showCultivo) }
    var isInfoSheetEnabled by remember { mutableStateOf(initialSettings.isInfoSheetEnabled) }
    var initialLocationSet by remember { mutableStateOf(false) }

    // --- PERSISTENCIA AUTOMÁTICA ---
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo, isInfoSheetEnabled) {
        MapSettingsStorage.saveSettings(
            context, 
            MapSettings(currentBaseMap.name, showRecinto, showCultivo, isInfoSheetEnabled)
        )
    }

    // --- ESTADO DATOS ---
    var visibleProjectIds by remember { mutableStateOf<Set<String>>(expedientes.map { it.id }.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    
    // Sincronizar visibilidad automáticamente al añadir nuevos expedientes (ej: Sin Proyecto)
    LaunchedEffect(expedientes) {
        visibleProjectIds = visibleProjectIds + expedientes.map { it.id }
    }
    
    // Datos Info
    var instantSigpacRef by remember { mutableStateOf("") }
    var recintoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var cultivoData by remember { mutableStateOf<Map<String, String>?>(null) }
    var lastDataId by remember { mutableStateOf<String?>(null) }
    var isLoadingData by remember { mutableStateOf(false) }
    var apiJob by remember { mutableStateOf<Job?>(null) }

    // Coordenadas del CENTRO del mapa (Retículo)
    var mapCenterLat by remember { mutableDoubleStateOf(0.0) }
    var mapCenterLng by remember { mutableDoubleStateOf(0.0) }

    // Coordenadas del USUARIO (GPS)
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLng by remember { mutableStateOf<Double?>(null) }
    var userAccuracy by remember { mutableStateOf<Float?>(null) }

    // --- OBTENCIÓN DE UBICACIÓN REAL (FUSED LOCATION) ---
    DisposableEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()
            
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    userLat = it.latitude
                    userLng = it.longitude
                    userAccuracy = it.accuracy
                }
            }
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
             try {
                 fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
             } catch (e: Exception) { e.printStackTrace() }
        }
        
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    // --- ESTADO GALERÍA (Desde Marcador Mapa) ---
    var selectedParcelForGallery by remember { mutableStateOf<NativeParcela?>(null) }
    var selectedExpForGallery by remember { mutableStateOf<NativeExpediente?>(null) }
    var initialGalleryIndex by remember { mutableIntStateOf(0) }

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
            } catch (e: Exception) { Log.e("MapScreen", "Map Lifecycle Error: ${e.message}") }
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
        try {
            mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(Expression.literal(false)) }
            mapInstance?.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(Expression.literal(false)) }
            mapInstance?.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } catch (e: Exception) { Log.e("MapError", "Error clearing search: ${e.message}") }
    }

    // --- FUNCIÓN PERFORM SEARCH ---
    fun performSearch() {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        isSearching = true
        searchActive = true

        scope.launch {
            val map = mapInstance ?: return@launch
            
            // 1. BÚSQUEDA LOCAL (Desde botón "Localizar en Mapa")
            if (searchQuery.startsWith("LOC:")) {
                val localId = searchQuery.substring(4)
                val targetParcel = expedientes.flatMap { it.parcelas }.find { it.id == localId }
                
                if (targetParcel != null) {
                    // UX Improvement: Reemplazamos el ID interno por la Referencia legible en la barra
                    searchQuery = targetParcel.referencia
                    instantSigpacRef = targetParcel.referencia // Abre la ficha de información

                    // Usar datos locales (SigpacInfo) si existen para rellenar la ficha inmediatamente
                    if (targetParcel.sigpacInfo != null) {
                        recintoData = mapOf(
                            "provincia" to (targetParcel.referencia.split(":")[0]),
                            "municipio" to (targetParcel.referencia.split(":")[1]),
                            "uso_sigpac" to (targetParcel.sigpacInfo.usoSigpac ?: ""),
                            "superficie" to (targetParcel.sigpacInfo.superficie.toString()),
                            "pendiente_media" to (targetParcel.sigpacInfo.pendienteMedia.toString()),
                            "altitud" to (targetParcel.sigpacInfo.altitud.toString()),
                            "coef_regadio" to (targetParcel.sigpacInfo.coefRegadio.toString()),
                            "incidencias" to (targetParcel.sigpacInfo.incidencias ?: "")
                        )
                    }

                    // Calcular geometría local (Polígono o Punto KML)
                    val localResult = computeLocalBoundsAndFeature(targetParcel)
                    if (localResult != null) {
                        try {
                            // Dibujar y hacer Zoom Suave
                            map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(localResult.feature)
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(localResult.bounds, 300), 3500)
                        } catch (e: Exception) { Log.e("MapError", "Error animating camera: ${e.message}") }
                    } else {
                        Toast.makeText(context, "Geometría pendiente de carga", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Parcela no encontrada localmente", Toast.LENGTH_SHORT).show()
                }
                isSearching = false
                return@launch
            }
            
            // 2. BÚSQUEDA REMOTA (Texto escrito manual: Prov:Mun...)
            val parts = searchQuery.split(":").map { it.trim() }
            if (parts.size < 4) {
                Toast.makeText(context, "Formato: Prov:Mun:Pol:Parc[:Rec]", Toast.LENGTH_LONG).show()
                isSearching = false
                return@launch
            }

            val prov = parts[0]; val mun = parts[1]; val pol = parts[2]; val parc = parts[3]; val rec = parts.getOrNull(4)

            // Filtro visual en capa MVT (Resaltado amarillo)
            try {
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
            } catch (e: Exception) { Log.e("MapError", "Error setting filter: ${e.message}") }

            // Búsqueda de Geometría en API
            val result = searchParcelLocation(prov, mun, pol, parc, rec)
            if (result != null) {
                try {
                    map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(result.feature)
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(result.bounds, 250), 3000)
                    instantSigpacRef = searchQuery // Abrir ficha
                } catch (e: Exception) { Log.e("MapError", "Error updating search result: ${e.message}") }
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

            // Click Listener para Marcadores de FOTOS
            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(screenPoint, com.geosigpac.cirserv.ui.map.LAYER_PHOTOS)
                
                if (features.isNotEmpty()) {
                    val feature = features[0]
                    if (feature.hasProperty("parcelId") && feature.hasProperty("expId") && feature.hasProperty("type")) {
                        // Verificamos que sea un feature de tipo foto
                        if (feature.getStringProperty("type") == "photo") {
                            val parcelId = feature.getStringProperty("parcelId")
                            val expId = feature.getStringProperty("expId")
                            val clickedUri = if(feature.hasProperty("uri")) feature.getStringProperty("uri") else null
                            
                            val exp = expedientes.find { it.id == expId }
                            val parcel = exp?.parcelas?.find { it.id == parcelId }
                            
                            if (exp != null && parcel != null && parcel.photos.isNotEmpty()) {
                                selectedExpForGallery = exp
                                selectedParcelForGallery = parcel
                                // Si clicamos una foto específica, intentamos abrir la galería en esa foto
                                initialGalleryIndex = if (clickedUri != null) {
                                    parcel.photos.indexOf(clickedUri).coerceAtLeast(0)
                                } else 0
                                return@addOnMapClickListener true
                            }
                        }
                    }
                }
                false
            }

            // Listener de Movimiento (Reset búsqueda si usuario mueve mapa)
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    if (searchActive) {
                        searchActive = false
                        // Limpiamos resultados visuales al mover el mapa manualmente
                        try {
                            map.style?.getSourceAs<GeoJsonSource>(SOURCE_SEARCH_RESULT)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                            val emptyFilter = Expression.literal(false)
                            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
                            map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
                        } catch (e: Exception) { Log.e("MapError", "Error clearing visual search: ${e.message}") }
                    }
                }
            }

            // Realtime Info - Actualización fluida durante movimiento
            map.addOnCameraMoveListener { 
                val target = map.cameraPosition.target
                if (target != null) {
                    mapCenterLat = target.latitude
                    mapCenterLng = target.longitude
                }

                if (!searchActive) {
                    val currentZoom = map.cameraPosition.zoom
                    
                    if (currentZoom > 13.5) { 
                        try {
                            val newRef = MapLogic.updateRealtimeInfo(map)
                            if (newRef.isNotEmpty()) {
                                instantSigpacRef = newRef
                            }
                        } catch (e: Exception) {
                            Log.e("MapScreen", "CRASH PREVENTED in MoveListener: ${e.message}")
                        }
                    } else {
                         if (instantSigpacRef.isNotEmpty()) {
                             instantSigpacRef = ""
                             try {
                                val emptyFilter = Expression.literal(false)
                                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_FILL)?.let { (it as FillLayer).setFilter(emptyFilter) }
                                map.style?.getLayer(LAYER_RECINTO_HIGHLIGHT_LINE)?.let { (it as LineLayer).setFilter(emptyFilter) }
                             } catch (e: Exception) {}
                         }
                    }
                }
            }

            // Extended Data (Carga de atributos al detenerse)
            map.addOnCameraIdleListener {
                val target = map.cameraPosition.target
                if (target != null) {
                    mapCenterLat = target.latitude
                    mapCenterLng = target.longitude
                }

                if (!searchActive) {
                    if (map.cameraPosition.zoom > 13.5) {
                        apiJob?.cancel()
                        apiJob = scope.launch {
                            isLoadingData = true
                            try {
                                val (r, c) = MapLogic.fetchExtendedData(map)
                                if (r != null) {
                                    val baseId = "${r["provincia"]}-${r["municipio"]}-${r["poligono"]}-${r["parcela"]}-${r["recinto"]}"
                                    val cultivoHash = c?.toString() ?: "null"
                                    val uniqueId = "$baseId|$cultivoHash"

                                    if (uniqueId != lastDataId) {
                                        lastDataId = uniqueId
                                        recintoData = r
                                        cultivoData = c
                                        instantSigpacRef = baseId.replace("-", ":")
                                    }
                                } else {
                                    lastDataId = null
                                    recintoData = null
                                    cultivoData = null
                                    instantSigpacRef = "" 
                                }
                            } catch (e: Exception) {
                                Log.e("MapScreen", "Error fetching idle data: ${e.message}")
                            } finally {
                                isLoadingData = false
                            }
                        }
                    }
                }
            }

            // Cargar Estilo Base
            val shouldCenterUser = !initialLocationSet && searchTarget.isNullOrEmpty()
            
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser) {
                initialLocationSet = true
            }
            
            // Configurar Capas de Proyectos (Ahora con Context para generar icono)
            MapLayers.setupProjectLayers(context, map)
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

    // --- TRIGGER BÚSQUEDA EXTERNA (Desde botón Localizar) ---
    LaunchedEffect(searchTarget, mapInstance) {
        if (!searchTarget.isNullOrEmpty() && mapInstance != null) {
            delay(800) 
            searchQuery = searchTarget
            performSearch()
        }
    }

    // --- TRIGGER UBICACIÓN USUARIO (Reset) ---
    LaunchedEffect(followUserTrigger) {
        if (followUserTrigger > 0 && mapInstance != null) {
            clearSearch()
            // FIX: Corregido error de argumentos sobrantes
            enableLocation(mapInstance, context, shouldCenter = true)
        }
    }

    // --- RECARGA ESTILO (CAPAS) ---
    LaunchedEffect(currentBaseMap, showRecinto, showCultivo) {
        mapInstance?.let { map ->
            loadMapStyle(map, currentBaseMap, showRecinto, showCultivo, context, shouldCenterUser = false) { }
            MapLayers.setupProjectLayers(context, map)
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
            isInfoSheetEnabled = isInfoSheetEnabled,
            expedientes = expedientes,
            visibleProjectIds = visibleProjectIds,
            instantSigpacRef = instantSigpacRef,
            recintoData = recintoData,
            cultivoData = cultivoData,
            // Pasamos ambas: Centro del Mapa (para navegación a punto) y Usuario (para display)
            mapCenterLat = mapCenterLat,
            mapCenterLng = mapCenterLng,
            userLat = userLat,
            userLng = userLng,
            userAccuracy = userAccuracy,
            onSearchQueryChange = { searchQuery = it },
            onSearchPerform = { performSearch() },
            onClearSearch = { clearSearch() },
            onChangeBaseMap = { currentBaseMap = it },
            onToggleRecinto = { showRecinto = it },
            onToggleCultivo = { showCultivo = it },
            onToggleInfoSheet = { isInfoSheetEnabled = it },
            onToggleProjectVisibility = { id -> 
                visibleProjectIds = if (visibleProjectIds.contains(id)) visibleProjectIds - id else visibleProjectIds + id 
            },
            onNavigateToProjects = onNavigateToProjects,
            onOpenCamera = onOpenCamera,
            onCenterLocation = { 
                // Lógica de Toggle: Centrado -> Brújula -> Centrado
                mapInstance?.locationComponent?.let { loc ->
                    if (!loc.isLocationComponentActivated || !loc.isLocationComponentEnabled) {
                        // FIX: Corregido error de argumentos sobrantes (enableLocation no toma lambda)
                        enableLocation(mapInstance, context, shouldCenter = true)
                        Toast.makeText(context, "Ubicación activada", Toast.LENGTH_SHORT).show()
                    } else {
                        // Alternar modos
                        if (loc.cameraMode == CameraMode.TRACKING) {
                            // Cambiar a modo Brújula (Mapa Rota según Orientación)
                            loc.cameraMode = CameraMode.TRACKING_COMPASS
                            loc.renderMode = RenderMode.COMPASS
                            Toast.makeText(context, "Modo Orientación (Mapa Rota)", Toast.LENGTH_SHORT).show()
                        } else {
                            // Volver a modo Centrado (Norte Arriba)
                            loc.cameraMode = CameraMode.TRACKING
                            loc.renderMode = RenderMode.COMPASS // Mantiene la brújula en el icono pero el mapa fijo al norte
                            Toast.makeText(context, "Modo Norte Arriba", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        
        // --- GALERÍA FOTOGRÁFICA SUPERPUESTA ---
        if (selectedParcelForGallery != null && selectedExpForGallery != null) {
            FullScreenPhotoGallery(
                photos = selectedParcelForGallery!!.photos,
                initialIndex = initialGalleryIndex,
                onDismiss = { selectedParcelForGallery = null },
                onDeletePhoto = { uriToDelete ->
                    val targetExp = selectedExpForGallery!!
                    val targetParcel = selectedParcelForGallery!!
                    
                    val updatedPhotos = targetParcel.photos.filter { it != uriToDelete }
                    val updatedLocs = targetParcel.photoLocations.toMutableMap().apply { remove(uriToDelete) }
                    
                    val updatedParcel = targetParcel.copy(photos = updatedPhotos, photoLocations = updatedLocs)
                    
                    val updatedExp = targetExp.copy(
                        parcelas = targetExp.parcelas.map { if (it.id == updatedParcel.id) updatedParcel else it }
                    )
                    
                    val updatedList = expedientes.map { if (it.id == updatedExp.id) updatedExp else it }
                    onUpdateExpedientes(updatedList)
                    
                    if (updatedPhotos.isEmpty()) {
                        selectedParcelForGallery = null 
                    } else {
                        selectedParcelForGallery = updatedParcel 
                        selectedExpForGallery = updatedExp
                    }
                }
            )
        }
    }
}
