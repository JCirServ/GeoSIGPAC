
package com.geosigpac.cirserv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

private const val TAG = "GeoSIGPAC_LOG_Main"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Iniciando actividad principal")
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        setContent {
            MaterialTheme {
                GeoSigpacApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Actividad destruida")
    }
}

@Composable
fun GeoSigpacApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isCameraOpen by remember { mutableStateOf(false) }
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val sharedPrefs = remember { context.getSharedPreferences("geosigpac_prefs", Context.MODE_PRIVATE) }
    var sessionLastUri by remember {
        val savedUriString = sharedPrefs.getString("last_photo_uri", null)
        mutableStateOf(if (savedUriString != null) Uri.parse(savedUriString) else null)
    }
    var sessionPhotoCount by remember { mutableIntStateOf(sharedPrefs.getInt("photo_count", 0)) }
    var selectedTab by remember { mutableIntStateOf(1) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val cameraGranted = perms[Manifest.permission.CAMERA] == true
            val locGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
            Log.d(TAG, "Permissions Result: Camera=$cameraGranted, Location=$locGranted")
            hasPermissions = cameraGranted && locGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            Log.d(TAG, "Solicitando permisos...")
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    val webAppInterface = remember {
        WebAppInterface(
            context = context,
            scope = scope,
            onCameraRequested = { projectId ->
                Log.i(TAG, "Cámara solicitada para proyecto: $projectId")
                if (hasPermissions) {
                    currentProjectId = projectId
                    isCameraOpen = true
                }
            },
            onMapFocusRequested = { lat, lng ->
                Log.i(TAG, "Enfoque de mapa solicitado: $lat, $lng")
                mapTarget = lat to lng
                selectedTab = 1
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (isCameraOpen) {
            CameraScreen(
                projectId = currentProjectId,
                lastCapturedUri = sessionLastUri,
                photoCount = sessionPhotoCount,
                onImageCaptured = { uri ->
                    Log.d(TAG, "Imagen capturada y guardada en: $uri")
                    sessionLastUri = uri
                    sessionPhotoCount++
                    sharedPrefs.edit().putString("last_photo_uri", uri.toString()).putInt("photo_count", sessionPhotoCount).apply()
                    scope.launch {
                        webViewRef?.evaluateJavascript("if(window.onPhotoCaptured) window.onPhotoCaptured('${currentProjectId}', '$uri');", null)
                    }
                },
                onError = { exc ->
                    Log.e(TAG, "Error en CameraScreen: ${exc.message}", exc)
                    isCameraOpen = false
                },
                onClose = { isCameraOpen = false },
                onGoToMap = { isCameraOpen = false; selectedTab = 1 },
                onGoToProjects = { isCameraOpen = false; selectedTab = 0 }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        Log.d(TAG, "Renderizando WebProjectManager")
                        WebProjectManager(
                            webAppInterface = webAppInterface,
                            onWebViewCreated = { webViewRef = it },
                            onNavigateToMap = { selectedTab = 1 },
                            onOpenCamera = { isCameraOpen = true }
                        )
                    }
                    1 -> {
                        Log.d(TAG, "Renderizando NativeMap")
                        NativeMap(
                            targetLat = mapTarget?.first,
                            targetLng = mapTarget?.second,
                            onNavigateToProjects = { selectedTab = 0 },
                            onOpenCamera = { isCameraOpen = true }
                        )
                    }
                }
            }
        }
    }
}
