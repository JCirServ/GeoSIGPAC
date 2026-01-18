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

    // Permission launcher that handles the user response
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        
        if (!cameraGranted || !locationGranted) {
            // In a real app, show a dialog explaining why you need permissions
            android.widget.Toast.makeText(this, "Se requieren permisos para la demo.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize MapLibre
        MapLibre.getInstance(this)
        
        // 2. Request Permissions immediately for this demo
        checkPermissions()
        
        enableEdgeToEdge()
        
        setContent {
            MainScreen()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // --- STATES ---
    var showCamera by remember { mutableStateOf(false) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    var targetLocation by remember { mutableStateOf<LatLng?>(null) }
    
    // References
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Animate map when targetLocation changes
    LaunchedEffect(targetLocation) {
        targetLocation?.let { loc ->
            mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 14.0))
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. NATIVE MAP (Top 40%)
                Box(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                onCreate(Bundle())
                                getMapAsync { map ->
                                    mapInstance = map
                                    map.setStyle("https://demotiles.maplibre.org/style.json") {
                                        map.cameraPosition = org.maplibre.gl.camera.CameraPosition.Builder()
                                            .target(LatLng(40.4168, -3.7038)) // Madrid default
                                            .zoom(5.0)
                                            .build()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.onStart() }
                    )
                }

                // 2. WEBVIEW (Bottom 60%)
                Box(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
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
                                    "Android"
                                )
                                
                                // Load from Assets
                                loadUrl("file:///android_asset/index.html")
                                
                                webViewClient = WebViewClient()
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. CAMERA OVERLAY
            if (showCamera) {
                CameraScreen(
                    onImageCaptured = { uri ->
                        val projectId = activeProjectId
                        if (projectId != null) {
                            webViewInstance?.post {
                                webViewInstance?.evaluateJavascript(
                                    "if(window.onPhotoCaptured) window.onPhotoCaptured('$projectId', '$uri');",
                                    null
                                )
                            }
                        }
                        showCamera = false
                    },
                    onError = { showCamera = false },
                    onClose = { showCamera = false }
                )
            }
        }
    }
}