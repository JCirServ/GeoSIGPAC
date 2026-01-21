
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
    var selectedTab by remember { mutableIntStateOf(1) } // 1 = Proyectos (Centro) por defecto
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // --- ESTADO PERSISTENTE DE EXPEDIENTES ---
    var expedientes by remember { mutableStateOf<List<NativeExpediente>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        expedientes = ProjectStorage.loadExpedientes(context)
    }

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
    ) { _ -> }

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
            onGoToMap = { 
                isCameraOpen = false
                mapTarget = null 
                selectedTab = 2 
            },
            onGoToProjects = { isCameraOpen = false; selectedTab = 1 }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF07080D),
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0D0E1A),
                    contentColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    // 1. CÁMARA (Izquierda)
                    NavigationBarItem(
                        selected = false,
                        onClick = { 
                            currentParcelaId = null
                            isCameraOpen = true 
                        },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Cámara") },
                        label = { Text("Cámara", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // 2. PROYECTOS (Centro)
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Proyectos") },
                        label = { Text("Proyectos", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00FF88),
                            selectedTextColor = Color(0xFF00FF88),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    // 3. MAPA (Derecha)
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { 
                            mapTarget = null
                            selectedTab = 2 
                        },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                        label = { Text("Mapa", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00FF88),
                            selectedTextColor = Color(0xFF00FF88),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), 
                color = Color(0xFF07080D)
            ) {
                when (selectedTab) {
                    1 -> NativeProjectManager(
                        expedientes = expedientes,
                        onUpdateExpedientes = { newList -> expedientes = newList },
                        onNavigateToMap = { lat, lng ->
                            mapTarget = if (lat != null && lng != null) lat to lng else null
                            selectedTab = 2
                        },
                        onOpenCamera = { id ->
                            currentParcelaId = id
                            isCameraOpen = true
                        }
                    )
                    2 -> NativeMap(
                        targetLat = mapTarget?.first,
                        targetLng = mapTarget?.second,
                        onNavigateToProjects = { selectedTab = 1 },
                        onOpenCamera = { 
                            currentParcelaId = null
                            isCameraOpen = true 
                        }
                    )
                }
            }
        }
    }
}
