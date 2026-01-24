
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
import com.geosigpac.cirserv.ui.map.NativeMapScreen
import com.geosigpac.cirserv.ui.NativeProjectManager
import com.geosigpac.cirserv.utils.ProjectStorage
import com.geosigpac.cirserv.utils.SigpacCodeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
        MapCacheManager.setupCache(this)
    
        enableEdgeToEdge()
        
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
    
    // FIX: Inicializar directamente desde storage para evitar el wipe de la lista vacía al arrancar
    var expedientes by remember { 
        mutableStateOf(ProjectStorage.loadExpedientes(context)) 
    }
    
    var isCameraOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(1) }
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    
    // Estados de navegación del mapa
    var mapSearchTarget by remember { mutableStateOf<String?>(null) }
    var followUserTrigger by remember { mutableLongStateOf(0L) } // Trigger para centrar en usuario
    var activeProjectId by remember { mutableStateOf<String?>(expedientes.firstOrNull()?.id) }

    // Inicialización de diccionarios (solo una vez)
    LaunchedEffect(Unit) {
        SigpacCodeManager.initialize(context)
    }

    // Persistencia reactiva: Guarda cada vez que la lista cambie
    LaunchedEffect(expedientes) {
        ProjectStorage.saveExpedientes(context, expedientes)
        
        // Mantener coherencia del proyecto activo
        if (activeProjectId != null && expedientes.none { it.id == activeProjectId }) {
            activeProjectId = expedientes.firstOrNull()?.id
        } else if (activeProjectId == null && expedientes.isNotEmpty()) {
            activeProjectId = expedientes.first().id
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
        if (permissionsToRequest.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    if (isCameraOpen) {
        BackHandler { isCameraOpen = false }
        CameraScreen(
            expedientes = expedientes, 
            projectId = currentParcelaId,
            lastCapturedUri = null,
            photoCount = 0,
            onUpdateExpedientes = { newList -> expedientes = newList },
            onImageCaptured = { /* Local handling */ },
            onError = { /* Error handling */ },
            onClose = { isCameraOpen = false },
            onGoToMap = { 
                isCameraOpen = false
                selectedTab = 2
                mapSearchTarget = null
                followUserTrigger = System.currentTimeMillis() // Forzar centrado en usuario
            },
            onGoToProjects = { isCameraOpen = false; selectedTab = 1 }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Solo mostramos la barra de navegación en la pantalla de Proyectos (Tab 1)
                // En el mapa (Tab 2) usamos los botones flotantes propios del mapa.
                if (selectedTab == 1) { 
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { isCameraOpen = true },
                            icon = { Icon(Icons.Default.CameraAlt, "Cámara") },
                            label = { Text("Cámara", fontSize = 13.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, unselectedIconColor = Color.Gray)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Folder, "Proyectos") },
                            label = { Text("Proyectos", fontSize = 13.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00FF88), selectedTextColor = Color(0xFF00FF88), indicatorColor = Color.Transparent)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { 
                                selectedTab = 2
                                mapSearchTarget = null
                                followUserTrigger = System.currentTimeMillis() // Forzar centrado en usuario
                            },
                            icon = { Icon(Icons.Default.Map, "Mapa") },
                            label = { Text("Mapa", fontSize = 13.sp) },
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
                        activeProjectId = activeProjectId,
                        onUpdateExpedientes = { newList -> 
                            expedientes = newList.toList() 
                        },
                        onActivateProject = { id -> activeProjectId = id },
                        onNavigateToMap = { query -> 
                            mapSearchTarget = query
                            selectedTab = 2 
                            // Aquí NO actualizamos followUserTrigger porque queremos ir a una parcela específica
                        },
                        onOpenCamera = { id -> currentParcelaId = id; isCameraOpen = true }
                    )
                    2 -> NativeMapScreen(
                        expedientes = expedientes, 
                        searchTarget = mapSearchTarget,
                        followUserTrigger = followUserTrigger,
                        onNavigateToProjects = { selectedTab = 1 },
                        onOpenCamera = { isCameraOpen = true }
                    )
                }
            }
        }
    }
}
