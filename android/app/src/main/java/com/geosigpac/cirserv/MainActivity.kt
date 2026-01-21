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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF88),
                    secondary = Color(0xFF62D2FF),
                    surface = Color(0xFF2D3033),
                    background = Color(0xFF1A1C1E),
                    onSurface = Color(0xFFE2E2E6),
                    outline = Color(0xFF44474B)
                )
            ) {
                GeoSigpacApp()
            }
        }
    }
}

@Composable
fun GeoSigpacApp() {
    val context = LocalContext.current
    var isCameraOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(1) }
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    var expedientes by remember { mutableStateOf<List<NativeExpediente>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        expedientes = ProjectStorage.loadExpedientes(context)
    }

    LaunchedEffect(expedientes) {
        ProjectStorage.saveExpedientes(context, expedientes)
    }

    val permissionsToRequest = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (permissionsToRequest.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
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
            onGoToMap = { isCameraOpen = false; selectedTab = 2 },
            onGoToProjects = { isCameraOpen = false; selectedTab = 1 }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (selectedTab == 1) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { isCameraOpen = true },
                            icon = { Icon(Icons.Default.CameraAlt, "Cámara") },
                            label = { Text("Cámara", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, unselectedIconColor = Color.Gray)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Folder, "Proyectos") },
                            label = { Text("Proyectos", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00FF88), selectedTextColor = Color(0xFF00FF88), indicatorColor = Color.Transparent)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Map, "Mapa") },
                            label = { Text("Mapa", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00FF88), selectedTextColor = Color(0xFF00FF88), indicatorColor = Color.Transparent)
                        )
                    }
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
                when (selectedTab) {
                    1 -> NativeProjectManager(
                        expedientes = expedientes,
                        onUpdateExpedientes = { newList -> expedientes = newList.toList() },
                        onNavigateToMap = { lat, lng -> mapTarget = if(lat != null) lat to lng!! else null; selectedTab = 2 },
                        onOpenCamera = { id -> currentParcelaId = id; isCameraOpen = true }
                    )
                    2 -> NativeMap(
                        targetLat = mapTarget?.first,
                        targetLng = mapTarget?.second,
                        expedientes = expedientes,
                        onNavigateToProjects = { selectedTab = 1 },
                        onOpenCamera = { isCameraOpen = true }
                    )
                }
            }
        }
    }
}
