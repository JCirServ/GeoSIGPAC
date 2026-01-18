package com.geosigpac.cirserv.ui

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Componente de Mapa Nativo usando MapLibre.
 * Escucha cambios en [targetLat] y [targetLng] para mover la cámara.
 */
@Composable
fun NativeMap(
    targetLat: Double?,
    targetLng: Double?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Inicializar MapLibre (requerido antes de inflar la vista)
    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // Manejo del Ciclo de Vida del Mapa
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // mapView.onDestroy() es manejado por el evento ON_DESTROY arriba
        }
    }

    // Efecto para mover la cámara cuando cambian las coordenadas desde la Web
    LaunchedEffect(targetLat, targetLng) {
        if (targetLat != null && targetLng != null) {
            mapView.getMapAsync { map ->
                val position = CameraPosition.Builder()
                    .target(LatLng(targetLat, targetLng))
                    .zoom(16.0)
                    .tilt(20.0)
                    .build()
                
                map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(position), 1500)
            }
        }
    }

    // Renderizado
    AndroidView(
        factory = { 
            mapView.getMapAsync { map ->
                // Cargar estilo Demotiles (Open source) para evitar configurar API Keys en este demo
                map.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                    // Estilo cargado
                }
                
                // Configuración UI
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
            }
            mapView 
        },
        modifier = Modifier.fillMaxSize()
    )
}