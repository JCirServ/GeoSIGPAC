
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF88),
                    surface = Color(0xFF07080D),
                    background = Color(0xFF07080D)
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
    var selectedTab by remember { mutableIntStateOf(0) } 
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    var mapTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var activeExpedienteId by remember { mutableStateOf<String?>(null) }

    var expedientes by remember { mutableStateOf<List<NativeExpediente>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        expedientes = ProjectStorage.loadExpedientes(context)
        if (expedientes.isNotEmpty()) activeExpedienteId = expedientes.first().id
    }

    LaunchedEffect(expedientes) {
        if (expedientes.isNotEmpty()) {
            ProjectStorage.saveExpedientes(context, expedientes)
        }
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
        val needsRequest = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) permissionLauncher.launch(permissionsToRequest)
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
                selectedTab = 1 
            },
            onGoToProjects = { isCameraOpen = false; selectedTab = 0 }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF07080D),
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0D0E1A),
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
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
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { 
                            mapTarget = null // Forzar zoom a mi posición al pulsar el botón de mapa
                            selectedTab = 1 
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
                modifier = Modifier.fillMaxSize().padding(paddingValues), 
                color = Color(0xFF07080D)
            ) {
                if (selectedTab == 0) {
                    NativeProjectManager(
                        expedientes = expedientes,
                        onUpdateExpedientes = { newList -> 
                            expedientes = newList
                            if (newList.isNotEmpty() && activeExpedienteId == null) activeExpedienteId = newList.first().id
                        },
                        onNavigateToMap = { lat, lng ->
                            if (lat != null && lng != null) mapTarget = lat to lng
                            selectedTab = 1
                        },
                        onOpenCamera = { id ->
                            currentParcelaId = id
                            isCameraOpen = true
                        }
                    )
                } else {
                    val activeParcelas = expedientes.find { it.id == activeExpedienteId }?.parcelas ?: emptyList()
                    NativeMap(
                        targetLat = mapTarget?.first,
                        targetLng = mapTarget?.second,
                        kmlParcelas = activeParcelas,
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
}
