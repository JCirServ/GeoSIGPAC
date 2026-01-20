
package com.geosigpac.cirserv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.CameraScreen
import com.geosigpac.cirserv.ui.NativeMap
import com.geosigpac.cirserv.ui.NativeProjectManager
import com.geosigpac.cirserv.utils.ProjectStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
        
        setContent {
            MaterialTheme {
                GeoSigpacApp()
            }
        }
    }
}

@Composable
fun GeoSigpacApp() {
    val context = LocalContext.current
    var isCameraOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Proyectos, 1 = Mapa
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // --- ESTADO PERSISTENTE DE EXPEDIENTES ---
    var expedientes by remember { mutableStateOf<List<NativeExpediente>>(emptyList()) }
    
    // Carga inicial desde disco
    LaunchedEffect(Unit) {
        expedientes = ProjectStorage.loadExpedientes(context)
    }

    // Guardado automático al cambiar la lista
    LaunchedEffect(expedientes) {
        if (expedientes.isNotEmpty() || ProjectStorage.loadExpedientes(context).isNotEmpty()) {
            ProjectStorage.saveExpedientes(context, expedientes)
        }
    }

    // GESTIÓN DE PERMISOS
    val permissionsToRequest = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
    }

    LaunchedEffect(Unit) {
        val needsRequest = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    if (isCameraOpen) {
        BackHandler { isCameraOpen = false }
        CameraScreen(
            projectId = currentParcelaId,
            lastCapturedUri = null,
            photoCount = 0,
            onImageCaptured = { isCameraOpen = false },
            onError = { isCameraOpen = false },
            onClose = { isCameraOpen = false },
            onGoToMap = { isCameraOpen = false; selectedTab = 1 },
            onGoToProjects = { isCameraOpen = false; selectedTab = 0 }
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF07080D)) {
            if (selectedTab == 0) {
                NativeProjectManager(
                    expedientes = expedientes,
                    onUpdateExpedientes = { newList -> expedientes = newList },
                    onNavigateToMap = { lat, lng ->
                        if (lat != null && lng != null) {
                            mapTarget = lat to lng
                        }
                        selectedTab = 1
                    },
                    onOpenCamera = { id ->
                        currentParcelaId = id
                        isCameraOpen = true
                    }
                )
            } else {
                NativeMap(
                    targetLat = mapTarget?.first,
                    targetLng = mapTarget?.second,
                    onNavigateToProjects = { selectedTab = 0 },
                    onOpenCamera = { 
                        currentParcelaId = null
                        isCameraOpen = true 
                    }
                )
            }
        }
    }
}
