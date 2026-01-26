
package com.geosigpac.cirserv.ui.camera

import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.BaseMap
import com.geosigpac.cirserv.ui.enableLocation
import com.geosigpac.cirserv.ui.loadMapStyle
import com.geosigpac.cirserv.ui.map.MapLayers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

private const val TAG = "MiniMapOverlay"

@Composable
fun MiniMapOverlay(
    modifier: Modifier = Modifier,
    userLocation: Location?,
    expedientes: List<NativeExpediente>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Estado del Mapa
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    
    // Lista de IDs visibles (todos por defecto en el minimapa para contexto)
    val visibleIds = remember(expedientes) { expedientes.map { it.id }.toSet() }

    // CRÍTICO: Asegurar que MapLibre Singleton está inicializado antes de crear la View
    remember {
        Log.d(TAG, "Inicializando Singleton MapLibre")
        MapLibre.getInstance(context)
    }

    // Inicializamos MapView con textureMode para evitar problemas de transparencia sobre CameraX
    val mapView = remember {
        Log.d(TAG, "Creando instancia de MapView (TextureMode=true)")
        try {
            val options = MapLibreMapOptions.createFromAttributes(context, null)
                .textureMode(true)
            
            MapView(context, options).apply {
                onCreate(Bundle())
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRASH al crear MapView: ${e.message}", e)
            throw e
        }
    }

    // Lifecycle Management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                Log.v(TAG, "Lifecycle Event: $event")
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> {
                        Log.d(TAG, "Destroying MapView")
                        mapView.onDestroy()
                    }
                    else -> {}
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Error en ciclo de vida ($event): ${e.message}", e)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        try {
            Log.d(TAG, "Forzando onStart inicial")
            mapView.onStart() // Forzar inicio inmediato
        } catch (e: Exception) {
            Log.e(TAG, "Error en onStart inicial: ${e.message}")
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { 
                Log.d(TAG, "Disposing MapView")
                mapView.onDestroy() 
            } catch (e: Exception) {
                Log.e(TAG, "Error en dispose: ${e.message}")
            }
        }
    }

    // Inicialización del Estilo y Capas
    LaunchedEffect(Unit) {
        Log.d(TAG, "Solicitando getMapAsync")
        mapView.getMapAsync { map ->
            Log.d(TAG, "Mapa listo. Configurando UI.")
            mapInstance = map
            
            // Configuración UI Minimalista
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false // Brújula ocupa mucho en mini mapa
                isTiltGesturesEnabled = false
                isRotateGesturesEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
            }

            // Cargar Estilo PNOA (Ortofoto) + Capas KML
            Log.d(TAG, "Cargando estilo PNOA")
            loadMapStyle(
                map = map,
                baseMap = BaseMap.PNOA, // Siempre PNOA para contexto real
                showRecinto = false,    // No cargamos catastro pesado en el mini mapa para rendimiento
                showCultivo = false,
                context = context,
                shouldCenterUser = false // Lo manejamos manualmente
            ) {
                Log.d(TAG, "Estilo cargado correctamente")
            }

            // Configurar capas de proyectos (KMLs)
            try {
                MapLayers.setupProjectLayers(context, map)
                
                // Pintar los proyectos iniciales
                scope.launch {
                    Log.d(TAG, "Actualizando capas de proyectos: ${expedientes.size} expedientes")
                    MapLayers.updateProjectsLayer(map, expedientes, visibleIds)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configurando capas: ${e.message}")
            }
        }
    }

    // Actualizar proyectos si cambian
    LaunchedEffect(expedientes) {
        mapInstance?.let { map ->
            if (map.style != null) {
                MapLayers.updateProjectsLayer(map, expedientes, visibleIds)
            }
        }
    }

    // Actualizar posición del usuario (Tracking suave)
    LaunchedEffect(userLocation, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect

        try {
            if (map.style != null) {
                // Asegurar que el componente de localización está activo
                enableLocation(map, context, shouldCenter = false)
                
                // Mover cámara suavemente
                val position = CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(16.0) // Zoom nivel parcela
                    .build()
                
                map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando cámara a ubicación usuario: ${e.message}")
        }
    }

    Box(
        modifier = modifier
            .size(130.dp) // Tamaño del Mini Mapa
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF00FF88), RoundedCornerShape(16.dp)) // Borde Neón
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Indicador central visual (opcional, ya que LocationComponent tiene su propio punto azul)
        // Dejamos que LocationComponent de MapLibre maneje el punto azul
    }
}
