
package com.geosigpac.cirserv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.geosigpac.cirserv.ui.CameraScreen
import com.geosigpac.cirserv.ui.map.NativeMapScreen
import com.geosigpac.cirserv.ui.NativeProjectManager
import com.geosigpac.cirserv.utils.ProjectStorage
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    
    // FIX: Inicializar directamente desde storage para evitar el wipe de la lista vacía al arrancar
    var expedientes by remember { 
        mutableStateOf(ProjectStorage.loadExpedientes(context)) 
    }
    
    // Configuración del Pager: 0 = Cámara, 1 = Proyectos (Inicio), 2 = Mapa
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    
    var currentParcelaId by remember { mutableStateOf<String?>(null) }
    
    // Estados de navegación del mapa
    var mapSearchTarget by remember { mutableStateOf<String?>(null) }
    var followUserTrigger by remember { mutableLongStateOf(0L) }
    var activeProjectId by remember { mutableStateOf<String?>(expedientes.firstOrNull()?.id) }

    // Inicialización de diccionarios (solo una vez)
    LaunchedEffect(Unit) {
        SigpacCodeManager.initialize(context)
    }

    // Persistencia reactiva: Guarda cada vez que la lista cambie
    LaunchedEffect(expedientes) {
        ProjectStorage.saveExpedientes(context, expedientes)
        
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

    // Manejo del botón Atrás
    BackHandler(enabled = pagerState.currentPage != 1) {
        // Si estamos en Cámara o Mapa, volver a Proyectos
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Ocultamos la barra de navegación en la Cámara (Page 0) para tener vista limpia
            // Se muestra en Proyectos (1) y Mapa (2)
            if (pagerState.currentPage != 0) { 
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Default.CameraAlt, "Cámara") },
                        label = { Text("Cámara", fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, unselectedIconColor = Color.Gray, selectedIconColor = Color(0xFF00FF88))
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Default.Folder, "Proyectos") },
                        label = { Text("Proyectos", fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00FF88), selectedTextColor = Color(0xFF00FF88), indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { 
                            mapSearchTarget = null
                            followUserTrigger = System.currentTimeMillis()
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        icon = { Icon(Icons.Default.Map, "Mapa") },
                        label = { Text("Mapa", fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00FF88), selectedTextColor = Color(0xFF00FF88), indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { padding ->
        // HorizontalPager maneja el swipe entre pantallas
        // userScrollEnabled = true permite el swipe manual
        // Nota: En la pantalla del Mapa, el mapa capturará los gestos horizontales primero. 
        // El usuario debe hacer swipe desde el borde o usar la barra inferior para salir del mapa.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = 1 // Mantiene las pantallas adyacentes en memoria para transiciones suaves
        ) { page ->
            when (page) {
                0 -> CameraScreen(
                    expedientes = expedientes, 
                    projectId = currentParcelaId,
                    lastCapturedUri = null,
                    photoCount = 0,
                    onUpdateExpedientes = { newList -> expedientes = newList },
                    onImageCaptured = { /* Local handling */ },
                    onError = { /* Error handling */ },
                    onClose = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onGoToMap = { 
                        mapSearchTarget = null
                        followUserTrigger = System.currentTimeMillis()
                        scope.launch { pagerState.animateScrollToPage(2) }
                    },
                    onGoToProjects = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> NativeProjectManager(
                    expedientes = expedientes,
                    activeProjectId = activeProjectId,
                    onUpdateExpedientes = { newList -> 
                        expedientes = newList.toList() 
                    },
                    onActivateProject = { id -> activeProjectId = id },
                    onNavigateToMap = { query -> 
                        mapSearchTarget = query
                        scope.launch { pagerState.animateScrollToPage(2) }
                    },
                    onOpenCamera = { id -> 
                        currentParcelaId = id
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                2 -> NativeMapScreen(
                    expedientes = expedientes, 
                    searchTarget = mapSearchTarget,
                    followUserTrigger = followUserTrigger,
                    onNavigateToProjects = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onOpenCamera = { scope.launch { pagerState.animateScrollToPage(0) } }
                )
            }
        }
    }
}
