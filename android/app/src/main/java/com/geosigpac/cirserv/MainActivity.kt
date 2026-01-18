package com.geosigpac.cirserv

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geosigpac.cirserv.bridge.WebAppInterface
import com.geosigpac.cirserv.ui.CameraScreen
import com.geosigpac.cirserv.ui.NativeMap
import com.geosigpac.cirserv.ui.WebProjectManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // --- MODO INMERSIVO (OCULTAR BARRAS) ---
        // Oculta la barra de estado y la barra de navegación para una experiencia Full Screen
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
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

    // --- PERMISOS ---
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // --- ESTADO DE LA APLICACIÓN ---
    var isCameraOpen by remember { mutableStateOf(hasPermissions) }
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // --- ESTADO DE SESIÓN DE FOTOS (Persistencia) ---
    // Inicializamos SharedPreferences
    val sharedPrefs = remember {
        context.getSharedPreferences("geosigpac_prefs", Context.MODE_PRIVATE)
    }

    // Cargamos estado guardado o usamos valores por defecto
    var sessionLastUri by remember {
        val savedUriString = sharedPrefs.getString("last_photo_uri", null)
        mutableStateOf(if (savedUriString != null) Uri.parse(savedUriString) else null)
    }
    
    var sessionPhotoCount by remember {
        mutableIntStateOf(sharedPrefs.getInt("photo_count", 0))
    }
    
    // Control de Pestañas (0 = Web, 1 = Mapa)
    var selectedTab by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val cameraGranted = perms[Manifest.permission.CAMERA] == true
            hasPermissions = cameraGranted
            // Si se conceden los permisos ahora, abrir la cámara automáticamente
            if (cameraGranted) {
                isCameraOpen = true
            }
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
                // 1. Actualizar coordenadas del mapa
                mapTarget = lat to lng
                // 2. Cambiar automáticamente a la pestaña del mapa
                selectedTab = 1
            }
        )
    }

    // --- RENDERIZADO ---
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isCameraOpen) {
            // PANTALLA CÁMARA (Modal Pantalla Completa)
            CameraScreen(
                projectId = currentProjectId, // Pasamos el ID del proyecto para la estructura de carpetas
                lastCapturedUri = sessionLastUri, // Pasamos el estado persistente
                photoCount = sessionPhotoCount,   // Pasamos el contador
                onImageCaptured = { uri ->
                    // 1. Actualizar Estado en Memoria
                    sessionLastUri = uri
                    val newCount = sessionPhotoCount + 1
                    sessionPhotoCount = newCount
                    
                    // 2. Persistir en SharedPreferences
                    sharedPrefs.edit().apply {
                        putString("last_photo_uri", uri.toString())
                        putInt("photo_count", newCount)
                        apply()
                    }
                    
                    val pid = currentProjectId
                    if (pid != null) {
                        scope.launch {
                            val jsCode = "if(window.onPhotoCaptured) window.onPhotoCaptured('$pid', '$uri');"
                            webViewRef?.evaluateJavascript(jsCode, null)
                        }
                    } 
                    // Feedback visual para el usuario
                    Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                },
                onError = { exc ->
                    Toast.makeText(context, "Error cámara: ${exc.message}", Toast.LENGTH_SHORT).show()
                    isCameraOpen = false
                },
                onClose = { isCameraOpen = false },
                onGoToMap = {
                    // Cerrar cámara y navegar al mapa
                    isCameraOpen = false
                    selectedTab = 1
                },
                onGoToProjects = {
                    // Cerrar cámara y navegar explícitamente a Proyectos
                    isCameraOpen = false
                    selectedTab = 0
                }
            )
        } else {
            // NAVEGACIÓN PRINCIPAL
            // Usamos Box para superponer o cambiar vistas sin BottomBar
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> WebProjectManager(
                        webAppInterface = webAppInterface,
                        onWebViewCreated = { webView -> webViewRef = webView },
                        onNavigateToMap = { selectedTab = 1 },
                        onOpenCamera = {
                            currentProjectId = null
                            isCameraOpen = true
                        }
                    )
                    1 -> NativeMap(
                        targetLat = mapTarget?.first,
                        targetLng = mapTarget?.second,
                        onNavigateToProjects = { selectedTab = 0 },
                        onOpenCamera = {
                            currentProjectId = null // Foto general desde el mapa
                            isCameraOpen = true
                        }
                    )
                }
            }
        }
    }
}