package com.geosigpac.cirserv

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.geosigpac.cirserv.bridge.WebAppInterface
import com.geosigpac.cirserv.ui.CameraScreen
import com.geosigpac.cirserv.ui.NativeMap
import com.geosigpac.cirserv.ui.WebProjectManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme {
                GeoSigpacApp()
            }
        }
    }
}

@Composable
fun GeoSigpacApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // --- ESTADO DE LA APLICACIÓN (Single Source of Truth) ---
    // Controla si mostramos la cámara a pantalla completa
    var isCameraOpen by remember { mutableStateOf(false) }
    // ID del proyecto que solicitó la foto
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    // Coordenadas para enfocar el mapa (null = sin acción)
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Referencia al WebView para ejecutar JS
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // --- PERMISOS ---
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            hasPermissions = perms[Manifest.permission.CAMERA] == true
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // --- INTERFAZ HÍBRIDA ---
    val webAppInterface = remember {
        WebAppInterface(
            context = context,
            scope = scope,
            onCameraRequested = { projectId ->
                if (hasPermissions) {
                    currentProjectId = projectId
                    isCameraOpen = true
                } else {
                    Toast.makeText(context, "Permisos de cámara requeridos", Toast.LENGTH_SHORT).show()
                }
            },
            onMapFocusRequested = { lat, lng ->
                mapTarget = lat to lng
            }
        )
    }

    // --- RENDERIZADO ---
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        
        if (isCameraOpen) {
            // PANTALLA CÁMARA (Nativa)
            CameraScreen(
                onImageCaptured = { uri ->
                    isCameraOpen = false
                    val pid = currentProjectId
                    if (pid != null) {
                        // Comunicación Android -> JS
                        // Pasamos la URI del archivo local para que la WebView la muestre
                        scope.launch {
                            val jsCode = "if(window.onPhotoCaptured) window.onPhotoCaptured('$pid', '$uri');"
                            webViewRef?.evaluateJavascript(jsCode, null)
                        }
                    }
                },
                onError = { exc ->
                    Toast.makeText(context, "Error cámara: ${exc.message}", Toast.LENGTH_SHORT).show()
                    isCameraOpen = false
                },
                onClose = {
                    isCameraOpen = false
                }
            )
        } else {
            // PANTALLA PRINCIPAL (Split View)
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. MAPA NATIVO (Parte superior 35%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f)
                        .background(Color.LightGray)
                ) {
                    NativeMap(
                        targetLat = mapTarget?.first, 
                        targetLng = mapTarget?.second
                    )
                }

                // 2. GESTOR WEB (Parte inferior 65%)
                // La WebView carga la UI principal en HTML/React
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.65f)
                ) {
                    WebProjectManager(
                        webAppInterface = webAppInterface,
                        onWebViewCreated = { webView ->
                            webViewRef = webView
                        }
                    )
                }
            }
        }
    }
}