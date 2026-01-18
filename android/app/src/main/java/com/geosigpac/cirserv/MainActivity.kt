package com.geosigpac.cirserv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.geosigpac.cirserv.bridge.WebAppInterface
import com.geosigpac.cirserv.ui.CameraScreen
import org.maplibre.gl.android.camera.CameraUpdateFactory
import org.maplibre.gl.android.maps.MapLibre
import org.maplibre.gl.android.maps.MapLibreMap
import org.maplibre.gl.android.maps.MapView
import org.maplibre.gl.geometry.LatLng

class MainActivity : ComponentActivity() {

    // Request permissions on startup for demo purposes
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize MapLibre
        MapLibre.getInstance(this)
        
        // 2. Request Permissions
        checkPermissions()
        
        enableEdgeToEdge()
        
        setContent {
            // Material Theme wrapper could go here
            MainScreen()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // --- STATES ---
    // Controls whether the camera UI is visible
    var showCamera by remember { mutableStateOf(false) }
    // Stores the ID of the project pending a photo
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    // Controls the target location for the Map
    var targetLocation by remember { mutableStateOf<LatLng?>(null) }
    
    // References to interact with views imperatively
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // --- EFFECTS ---
    // Animate map when targetLocation changes (Triggered from Web)
    LaunchedEffect(targetLocation) {
        targetLocation?.let { loc ->
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(loc, 14.0)
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // MAIN LAYOUT: Split View
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. TOP: NATIVE MAP
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                onCreate(Bundle()) // Mandatory for MapLibre lifecycle
                                getMapAsync { map ->
                                    mapInstance = map
                                    map.setStyle("https://demotiles.maplibre.org/style.json") {
                                        // Default View: Spain
                                        map.cameraPosition = org.maplibre.gl.camera.CameraPosition.Builder()
                                            .target(LatLng(40.4168, -3.7038))
                                            .zoom(5.0)
                                            .build()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            mapView.onStart() // Ensure map renders
                        }
                    )
                }

                // 2. BOTTOM: WEBVIEW (Project Manager)
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Web Settings
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                
                                // Inject Bridge
                                addJavascriptInterface(
                                    WebAppInterface(
                                        context = ctx,
                                        scope = scope,
                                        onCameraRequested = { projectId ->
                                            activeProjectId = projectId
                                            showCamera = true
                                        },
                                        onMapFocusRequested = { lat, lng ->
                                            targetLocation = LatLng(lat, lng)
                                        }
                                    ),
                                    "Android" // window.Android
                                )
                                
                                loadUrl("file:///android_asset/index.html")
                                webViewClient = WebViewClient()
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. OVERLAY: CAMERA SCREEN
            if (showCamera) {
                CameraScreen(
                    onImageCaptured = { uri ->
                        // Success: Send URI back to WebView
                        val projectId = activeProjectId
                        if (projectId != null) {
                            webViewInstance?.post {
                                // Calls: window.onPhotoCaptured(id, uri)
                                webViewInstance?.evaluateJavascript(
                                    "if(window.onPhotoCaptured) window.onPhotoCaptured('$projectId', '$uri');",
                                    null
                                )
                            }
                        }
                        showCamera = false
                    },
                    onError = { exc ->
                        android.util.Log.e("Main", "Camera Error", exc)
                        showCamera = false
                    },
                    onClose = {
                        showCamera = false
                    }
                )
            }
        }
    }
}
