package com.geosigpac.cirserv

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geosigpac.cirserv.bridge.WebAppInterface
import com.geosigpac.cirserv.ui.CameraScreen
import org.maplibre.gl.android.maps.MapLibreMap
import org.maplibre.gl.android.maps.MapView
import org.maplibre.gl.camera.CameraUpdateFactory
import org.maplibre.gl.geometry.LatLng

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MapLibre
        org.maplibre.gl.android.maps.MapLibre.getInstance(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // --- State ---
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // UI Visibility State
    var isCameraVisible by remember { mutableStateOf(false) }
    var currentProjectId by remember { mutableStateOf<String?>(null) }

    // --- Permissions ---
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                isCameraVisible = true
            } else {
                Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // --- Layout ---
    // We use a Box to overlay the Camera on top of the Map/Web without destroying the WebView
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Layer 1: Main Hybrid Interface (Map + Web)
        Column(modifier = Modifier.fillMaxSize()) {
            
            // MapView
            AndroidView(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(Bundle())
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.setStyle("https://demotiles.maplibre.org/style.json") {
                                map.cameraPosition = org.maplibre.gl.camera.CameraPosition.Builder()
                                    .target(LatLng(40.4168, -3.7038)) 
                                    .zoom(5.0)
                                    .build()
                            }
                        }
                    }
                },
                update = { mapView -> 
                    lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                            else -> {}
                        }
                    })
                }
            )

            // WebView
            AndroidView(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true 
                        
                        // Disable native scrollbars
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        
                        addJavascriptInterface(
                            WebAppInterface(
                                context,
                                scope,
                                onCameraRequested = { projectId ->
                                    currentProjectId = projectId
                                    if (hasCameraPermission) {
                                        isCameraVisible = true
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onMapFocusRequested = { lat, lng ->
                                    mapLibreMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0)
                                    )
                                }
                            ),
                            "Android"
                        )
                        
                        webViewClient = WebViewClient()
                        loadUrl("file:///android_asset/index.html")
                        webViewRef = this
                    }
                }
            )
        }

        // Layer 2: Custom Camera Overlay
        if (isCameraVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f) // Ensure it's on top
            ) {
                CameraScreen(
                    onImageCaptured = { uri ->
                        // Send URI back to Web
                        isCameraVisible = false
                        currentProjectId?.let { id ->
                            // Convert File URI to String. 
                            // Since we set allowFileAccess=true, file:// works.
                            val uriString = uri.toString()
                            
                            webViewRef?.post {
                                webViewRef?.evaluateJavascript(
                                    "if(window.onPhotoCaptured) window.onPhotoCaptured('$id', '$uriString');",
                                    null
                                )
                            }
                        }
                    },
                    onError = {
                        Toast.makeText(context, "Error al capturar foto", Toast.LENGTH_SHORT).show()
                    },
                    onClose = {
                        isCameraVisible = false
                    }
                )
            }
        }
    }
}