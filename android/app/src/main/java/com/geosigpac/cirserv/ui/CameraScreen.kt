
package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.geosigpac.cirserv.model.NativeExpediente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// --- ENUMS CONFIGURACIÓN ---
enum class CamAspectRatio(val label: String, val ratioConstant: Int, val isFull: Boolean = false) {
    SQUARE("1:1", AspectRatio.RATIO_4_3),
    RATIO_4_3("4:3", AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", AspectRatio.RATIO_16_9),
    FULL("Full", AspectRatio.RATIO_16_9, true)
}

enum class CamQuality(val label: String, val targetSize: Size?) {
    LOW("Baja", Size(640, 480)),
    MEDIUM("Media", Size(1280, 720)),
    HIGH("Alta", Size(1920, 1080)),
    VERY_HIGH("Muy Alta", Size(3840, 2160)),
    MAX("Máxima", null)
}

// --- PERSISTENCIA DE CONFIGURACIÓN ---
object CameraPreferences {
    private const val PREFS_NAME = "geosigpac_camera_prefs"
    private const val KEY_RATIO = "cam_ratio"
    private const val KEY_QUALITY = "cam_quality"
    private const val KEY_FLASH = "cam_flash"
    private const val KEY_GRID = "cam_grid"

    fun getRatio(context: Context): CamAspectRatio {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_RATIO, CamAspectRatio.RATIO_4_3.name)
        return try { CamAspectRatio.valueOf(name!!) } catch (e: Exception) { CamAspectRatio.RATIO_4_3 }
    }

    fun setRatio(context: Context, ratio: CamAspectRatio) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_RATIO, ratio.name).apply()
    }

    fun getQuality(context: Context): CamQuality {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_QUALITY, CamQuality.HIGH.name)
        return try { CamQuality.valueOf(name!!) } catch (e: Exception) { CamQuality.HIGH }
    }

    fun setQuality(context: Context, quality: CamQuality) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_QUALITY, quality.name).apply()
    }

    fun getFlashMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_FLASH, ImageCapture.FLASH_MODE_AUTO)
    }

    fun setFlashMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_FLASH, mode).apply()
    }

    fun getGrid(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_GRID, false)
    }

    fun setGrid(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_GRID, enabled).apply()
    }
}

// Helper para normalizar referencias (elimina ceros a la izquierda: 46:01 -> 46:1)
fun normalizeSigpacRef(ref: String?): String {
    if (ref == null) return ""
    return ref.split(":", "-")
        .joinToString(":") { part -> 
            part.trim().toIntOrNull()?.toString() ?: part.trim() 
        }
}

// Helper para Point In Polygon (Ray Casting)
fun isPointInPolygon(lat: Double, lng: Double, geometryRaw: String?): Boolean {
    if (geometryRaw.isNullOrEmpty()) return false
    try {
        val polyPoints = geometryRaw.trim().split("\\s+".toRegex()).mapNotNull { 
            val parts = it.split(",")
            if (parts.size >= 2) {
                val pLng = parts[0].toDoubleOrNull()
                val pLat = parts[1].toDoubleOrNull()
                if (pLng != null && pLat != null) pLat to pLng else null
            } else null
        }
        if (polyPoints.isEmpty()) return false
        
        var inside = false
        var j = polyPoints.lastIndex
        for (i in polyPoints.indices) {
            val (latI, lngI) = polyPoints[i]
            val (latJ, lngJ) = polyPoints[j]
            if (((latI > lat) != (latJ > lat)) &&
                (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)) {
                inside = !inside
            }
            j = i
        }
        return inside
    } catch (e: Exception) { return false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    expedientes: List<NativeExpediente>,
    projectId: String?,
    lastCapturedUri: Uri?,
    photoCount: Int,
    onUpdateExpedientes: (List<NativeExpediente>) -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClose: () -> Unit,
    onGoToMap: () -> Unit,
    onGoToProjects: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Estados Cámara (Inicializados desde Preferencias)
    var selectedRatio by remember { mutableStateOf(CameraPreferences.getRatio(context)) }
    var selectedQuality by remember { mutableStateOf(CameraPreferences.getQuality(context)) }
    var flashMode by remember { mutableIntStateOf(CameraPreferences.getFlashMode(context)) }
    var showGrid by remember { mutableStateOf(CameraPreferences.getGrid(context)) }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // UI States
    var showParcelSheet by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    // CameraX
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }

    // Zoom & Focus
    var currentLinearZoom by remember { mutableFloatStateOf(0f) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // GPS & SIGPAC
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }

    // --- NUEVO: REFERENCIA MANUAL ---
    // Si el GPS falla, el usuario puede introducir la referencia manualmente
    var manualRef by remember { mutableStateOf<String?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualInputBuffer by remember { mutableStateOf("") }

    // Referencia Efectiva: Prioriza la manual, sino la del GPS
    val effectiveRef = manualRef ?: sigpacRef

    // --- LÓGICA DE DETECCIÓN DE RECINTO EN PROYECTO (SOLO GEOMETRÍA) ---
    val matchedParcelInfo = remember(expedientes, currentLocation) {
        val curLat = currentLocation?.latitude
        val curLng = currentLocation?.longitude
        
        if (curLat == null || curLng == null) return@remember null

        var foundExp: NativeExpediente? = null
        val foundParcel = expedientes.flatMap { exp ->
            exp.parcelas.map { p -> 
                // Coincidencia ESTRICTA por GEOMETRÍA (Ray Casting)
                if (!p.geometryRaw.isNullOrEmpty() && isPointInPolygon(curLat, curLng, p.geometryRaw)) {
                    foundExp = exp
                    p 
                } else null
            }
        }.filterNotNull().firstOrNull()

        if (foundParcel != null && foundExp != null) {
            Pair(foundExp!!, foundParcel)
        } else null
    }

    // --- DETERMINAR DATOS DE GUARDADO (Variable para la UI) ---
    val uiContextData = remember(matchedParcelInfo, projectId, expedientes, effectiveRef) {
        if (matchedParcelInfo != null) {
            Triple(matchedParcelInfo.first.titular, matchedParcelInfo.second.referencia, true)
        } else if (projectId != null) {
            val foundExp = expedientes.find { exp -> exp.parcelas.any { it.id == projectId } }
            val foundParcel = foundExp?.parcelas?.find { it.id == projectId }
            val folderName = foundExp?.titular ?: "SIN PROYECTO"
            val refName = foundParcel?.referencia ?: effectiveRef ?: "SIN_REFERENCIA"
            Triple(folderName, refName, foundExp != null)
        } else {
            Triple("SIN PROYECTO", effectiveRef ?: "SIN_REFERENCIA", false)
        }
    }

    // Bitmap Preview
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val targetPreviewUri = remember(matchedParcelInfo, lastCapturedUri) {
        if (matchedParcelInfo != null && matchedParcelInfo.second.photos.isNotEmpty()) {
            Uri.parse(matchedParcelInfo.second.photos.last())
        } else {
            lastCapturedUri
        }
    }
    
    val currentPhotoCount = remember(matchedParcelInfo, photoCount) {
        if (matchedParcelInfo != null) matchedParcelInfo.second.photos.size else photoCount
    }

    // (Carga de Bitmap)
    LaunchedEffect(targetPreviewUri) {
        targetPreviewUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    var finalBitmap = bitmap
                    if (bitmap != null) {
                        capturedBitmap = finalBitmap?.asImageBitmap()
                    }
                } catch (e: Exception) { }
            }
        } ?: run { capturedBitmap = null }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label = "Blink"
    )
    
    val MapIcon = remember {
        ImageVector.Builder(name = "Map", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.White)) { moveTo(20.5f, 3.0f); lineTo(20.34f, 3.03f); lineTo(15.0f, 5.1f); lineTo(9.0f, 3.0f); lineTo(3.36f, 4.9f); curveTo(3.15f, 4.97f, 3.0f, 5.15f, 3.0f, 5.38f); verticalLineTo(20.5f); curveTo(3.0f, 20.78f, 3.22f, 21.0f, 3.5f, 21.0f); lineTo(3.66f, 20.97f); lineTo(9.0f, 18.9f); lineTo(15.0f, 21.0f); lineTo(20.64f, 19.1f); curveTo(20.85f, 19.03f, 21.0f, 18.85f, 21.0f, 18.62f); verticalLineTo(3.5f); curveTo(21.0f, 3.22f, 20.78f, 3.0f, 20.5f, 3.0f); close(); moveTo(15.0f, 19.0f); lineTo(9.0f, 16.89f); verticalLineTo(5.0f); lineTo(15.0f, 7.11f); verticalLineTo(19.0f); close() }
        }.build()
    }

    // Zoom Observer
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        cam.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
            currentLinearZoom = state.linearZoom; currentZoomRatio = state.zoomRatio
        }
    }

    // Camera Bind
    LaunchedEffect(selectedRatio, selectedQuality, flashMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            val resolutionStrategy = if (selectedQuality == CamQuality.MAX) ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY else ResolutionStrategy(selectedQuality.targetSize!!, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            val aspectRatioStrategy = if (selectedRatio == CamAspectRatio.FULL) AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO) else AspectRatioStrategy(selectedRatio.ratioConstant, AspectRatioStrategy.FALLBACK_RULE_AUTO)
            val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(resolutionStrategy).setAspectRatioStrategy(aspectRatioStrategy).build()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            if (selectedRatio == CamAspectRatio.FULL) previewView.scaleType = PreviewView.ScaleType.FILL_CENTER else previewView.scaleType = PreviewView.ScaleType.FIT_CENTER 
            val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setResolutionSelector(resolutionSelector).setFlashMode(flashMode).build()
            try {
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                imageCaptureUseCase = imageCapture
            } catch (exc: Exception) { Log.e("CameraScreen", "Binding failed", exc) }
        }, ContextCompat.getMainExecutor(context))
    }

    // Bucle SIGPAC
    LaunchedEffect(manualRef) {
        while (manualRef == null) {
            val loc = currentLocation
            val now = System.currentTimeMillis()
            if (loc != null) {
                val distance = if (lastApiLocation != null) loc.distanceTo(lastApiLocation!!) else Float.MAX_VALUE
                val timeElapsed = now - lastApiTimestamp
                val shouldFetch = lastApiLocation == null || distance > 3.0f || timeElapsed > 5000
                if (shouldFetch && !isLoadingSigpac) {
                    isLoadingSigpac = true
                    lastApiLocation = loc
                    lastApiTimestamp = now
                    try {
                        val (ref, uso) = fetchRealSigpacData(loc.latitude, loc.longitude)
                        if (ref != null) { sigpacRef = ref; sigpacUso = uso; showNoDataMessage = false } 
                        else { sigpacRef = null; sigpacUso = null; delay(2000); if (sigpacRef == null) showNoDataMessage = true }
                    } catch (e: Exception) { } finally { isLoadingSigpac = false }
                }
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { currentLocation = location }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { locationText = "Sin señal GPS" }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener) } catch (e: Exception) {}
        }
        onDispose { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locationManager.removeUpdates(listener) }
    }

    // UI Styles
    val NeonGreen = Color(0xFF00FF88)
    val WarningRed = Color(0xFFFF5252)

    val ProjectsBtn = @Composable { Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable { onGoToProjects() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.List, "Proyectos", tint = NeonGreen) } }
    val SettingsBtn = @Composable { Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable { showSettingsDialog = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, "Configuración", tint = NeonGreen) } }
    val MatchInfoBtn = @Composable { if (matchedParcelInfo != null) { Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.7f)).clickable { showParcelSheet = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, contentDescription = "Info Parcela", tint = NeonGreen.copy(alpha = blinkAlpha), modifier = Modifier.size(32.dp)) } } }

    val InfoBox = @Composable {
        Box(
            modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (manualRef != null) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("REF. MANUAL", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Close, "Borrar manual", tint = Color.Gray, modifier = Modifier.size(16.dp).clickable { manualRef = null; sigpacRef = null })
                    }
                    Text(manualRef!!, color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
                } else if (sigpacRef != null) {
                    Text("GPS SIGPAC", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(sigpacRef!!, color = Color(0xFFFFFF00), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
                    Text("Uso: ${sigpacUso ?: "N/D"}", color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                    if (matchedParcelInfo != null) Text("EN PROYECTO", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                } else if (showNoDataMessage) {
                    Text("Sin datos SIGPAC", color = WarningRed, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                } else {
                    Text("Analizando zona...", color = Color.LightGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    val ShutterButton = @Composable {
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, if(effectiveRef != null || uiContextData.second != "SIN_REFERENCIA") NeonGreen else Color.Gray, CircleShape)
                .padding(6.dp)
                .background(if(effectiveRef != null || uiContextData.second != "SIN_REFERENCIA") NeonGreen else Color.Transparent, CircleShape)
                .clickable {
                    // Si no hay referencia, mostramos teclado
                    if (uiContextData.second == "SIN_REFERENCIA" && effectiveRef == null) {
                         manualInputBuffer = ""
                         showManualInput = true
                    } else {
                        // LÓGICA DE DISPARO ROBUSTA
                        val currentMatch = matchedParcelInfo 
                        val finalFolderName = if (currentMatch != null) currentMatch.first.titular else uiContextData.first
                        val finalRefName = if (currentMatch != null) currentMatch.second.referencia else uiContextData.second
                        val finalIsProjectActive = currentMatch != null || uiContextData.third
                        
                        takePhoto(context, imageCaptureUseCase, finalFolderName, finalRefName, finalIsProjectActive,
                            onImageCaptured = { uri -> 
                                if (currentMatch != null) {
                                    val (exp, parc) = currentMatch
                                    val updatedParcela = parc.copy(photos = parc.photos + uri.toString())
                                    val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })
                                    onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
                                    Toast.makeText(context, "Guardada en: ${exp.titular}", Toast.LENGTH_SHORT).show()
                                } else if (finalIsProjectActive) {
                                    Toast.makeText(context, "Guardada en: $finalFolderName", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Guardada en: SIN PROYECTO", Toast.LENGTH_SHORT).show()
                                }
                                onImageCaptured(uri) 
                            }, onError)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (effectiveRef == null && uiContextData.second == "SIN_REFERENCIA") {
                Text("REF", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Gray)
            }
        }
    }

    val PreviewButton = @Composable {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp)) 
                    .background(Color.Black.copy(0.5f))
                    .border(2.dp, NeonGreen, RoundedCornerShape(24.dp))
                    .clickable { 
                        if (matchedParcelInfo != null && matchedParcelInfo!!.second.photos.isNotEmpty()) showGallery = true
                        else onClose()
                    }, 
                contentAlignment = Alignment.Center
            ) { 
                if (capturedBitmap != null) Image(bitmap = capturedBitmap!!, contentDescription = "Preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(imageVector = Icons.Default.Image, contentDescription = "Sin Foto", tint = NeonGreen, modifier = Modifier.size(36.dp))
            }
            if (currentPhotoCount > 0) {
                Box(modifier = Modifier.offset(x = 8.dp, y = (-8).dp).size(28.dp).background(Color.Red, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = currentPhotoCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- MAIN UI ---
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> camera?.let { cam -> cam.cameraControl.setZoomRatio((cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f) * zoom) } } }
            .pointerInput(Unit) { detectTapGestures { offset -> showFocusRing = true; focusRingPosition = offset; scope.launch { delay(1000); showFocusRing = false }; camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat()).createPoint(offset.x, offset.y), FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build()) } }
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); } })
        
        if (showFocusRing && focusRingPosition != null) {
            Box(modifier = Modifier.offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.x.toDp() - 25.dp }, y = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.y.toDp() - 25.dp }).size(50.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        }

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height; drawLine(Color.White.copy(0.3f), Offset(w/3, 0f), Offset(w/3, h), 2f); drawLine(Color.White.copy(0.3f), Offset(2*w/3, 0f), Offset(2*w/3, h), 2f); drawLine(Color.White.copy(0.3f), Offset(0f, h/3), Offset(w, h/3), 2f); drawLine(Color.White.copy(0.3f), Offset(0f, 2*h/3), Offset(w, 2*h/3), 2f)
            }
        }

        if (selectedRatio == CamAspectRatio.SQUARE) {
            val maskColor = Color.Black.copy(alpha = 0.5f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.minDimension; val ox = (size.width - s) / 2; val oy = (size.height - s) / 2
                drawRect(maskColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, oy))
                drawRect(maskColor, topLeft = Offset(0f, oy + s), size = androidx.compose.ui.geometry.Size(size.width, size.height - (oy + s)))
                drawRect(maskColor, topLeft = Offset(0f, oy), size = androidx.compose.ui.geometry.Size(ox, s))
                drawRect(maskColor, topLeft = Offset(ox + s, oy), size = androidx.compose.ui.geometry.Size(size.width - (ox + s), s))
            }
        }

        // --- LAYOUTS LANDSCAPE/PORTRAIT ---
        if (isLandscape) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { SettingsBtn(); ProjectsBtn(); MatchInfoBtn() } }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { InfoBox() }
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) { ShutterButton() }
            Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) { PreviewButton(); Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(0.5f)).border(2.dp, NeonGreen, RoundedCornerShape(24.dp)).clickable { onGoToMap() }, contentAlignment = Alignment.Center) { Icon(MapIcon, "Mapa", tint = NeonGreen, modifier = Modifier.size(36.dp)) } }
        } else {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { SettingsBtn(); ProjectsBtn(); MatchInfoBtn() } }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) { InfoBox() }
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { PreviewButton(); ShutterButton(); Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(0.5f)).border(2.dp, NeonGreen, RoundedCornerShape(24.dp)).clickable { onGoToMap() }, contentAlignment = Alignment.Center) { Icon(MapIcon, "Mapa", tint = NeonGreen, modifier = Modifier.size(36.dp)) } }
            }
        }
        
        // --- KEYBOARD OVERLAY (Manual Entry) ---
        AnimatedVisibility(
            visible = showManualInput,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CameraSigpacKeyboard(
                buffer = manualInputBuffer,
                onKey = { manualInputBuffer += it },
                onBackspace = { if (manualInputBuffer.isNotEmpty()) manualInputBuffer = manualInputBuffer.dropLast(1) },
                onConfirm = { 
                    if (manualInputBuffer.isNotEmpty()) {
                        manualRef = manualInputBuffer
                        showManualInput = false
                    }
                },
                onClose = { showManualInput = false }
            )
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configuración de Cámara") },
                text = { 
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Relación de Aspecto", fontWeight = FontWeight.Bold, color = NeonGreen)
                        CamAspectRatio.values().forEach { r -> 
                            Row(Modifier.fillMaxWidth().clickable { 
                                selectedRatio = r
                                CameraPreferences.setRatio(context, r)
                            }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                                RadioButton(selectedRatio == r, { 
                                    selectedRatio = r 
                                    CameraPreferences.setRatio(context, r)
                                })
                                Text(r.label) 
                            } 
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text("Calidad", fontWeight = FontWeight.Bold, color = NeonGreen)
                        CamQuality.values().forEach { q -> 
                            Row(Modifier.fillMaxWidth().clickable { 
                                selectedQuality = q
                                CameraPreferences.setQuality(context, q)
                            }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                                RadioButton(selectedQuality == q, { 
                                    selectedQuality = q 
                                    CameraPreferences.setQuality(context, q)
                                })
                                Text(q.label) 
                            } 
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text("Opciones", fontWeight = FontWeight.Bold, color = NeonGreen)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Flash")
                            Row {
                                val modes = listOf(ImageCapture.FLASH_MODE_AUTO to Icons.Default.FlashAuto, ImageCapture.FLASH_MODE_ON to Icons.Default.FlashOn, ImageCapture.FLASH_MODE_OFF to Icons.Default.FlashOff)
                                modes.forEach { (m, icon) ->
                                    IconButton(onClick = { flashMode = m; CameraPreferences.setFlashMode(context, m) }) {
                                        Icon(icon, null, tint = if (flashMode == m) NeonGreen else Color.Gray)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cuadrícula")
                            IconButton(onClick = { showGrid = !showGrid; CameraPreferences.setGrid(context, !showGrid) }) {
                                Icon(if (showGrid) Icons.Default.Grid3x3 else Icons.Default.GridOff, null, tint = if (showGrid) NeonGreen else Color.Gray)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ showSettingsDialog = false }) { Text("Cerrar") } }
            )
        }
        
        if (showParcelSheet) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { showParcelSheet = false }) }
        AnimatedVisibility(visible = showParcelSheet && matchedParcelInfo != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            matchedParcelInfo?.let { (exp, parc) ->
                 Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        NativeRecintoCard(parcela = parc, onLocate = { onGoToMap() }, onCamera = { showParcelSheet = false }, onUpdateParcela = { updatedParcela -> val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }); onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it }) }, initiallyExpanded = true, initiallyTechExpanded = true)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
        
        if (showGallery && matchedParcelInfo != null) {
            val (exp, parc) = matchedParcelInfo!!
            FullScreenPhotoGallery(photos = parc.photos, initialIndex = parc.photos.lastIndex, onDismiss = { showGallery = false }, onDeletePhoto = { photoUri -> val updatedPhotos = parc.photos.filter { it != photoUri }; val updatedParcela = parc.copy(photos = updatedPhotos); val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }); onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it }) })
        }
    }
}

// --- LOCAL KEYBOARD COMPONENT FOR CAMERA ---
@Composable
fun CameraSigpacKeyboard(
    buffer: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF252525)).padding(8.dp).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("INTRODUCIR REF. SIGPAC", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = Color.Gray) }
        }
        // Input Preview
        Box(modifier = Modifier.fillMaxWidth().padding(bottom=10.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
            if (buffer.isEmpty()) Text("Prov:Mun:Agg:Zon:Pol:Parc:Rec", color = Color.Gray)
            else Text(buffer, color = Color(0xFF00FF88), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(":", "0", "DEL"))
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f).height(50.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF121212)).clickable { if (key == "DEL") onBackspace() else onKey(key) }, contentAlignment = Alignment.Center) {
                        if (key == "DEL") Icon(Icons.Default.Backspace, "Borrar", tint = Color.White) else Text(text = key, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 22.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Check, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("CONFIRMAR REFERENCIA", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        }
    }
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.requestMethod = "GET"; connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")
        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)
            reader.close(); connection.disconnect()
            val jsonResponse = response.toString().trim()
            var targetJson: JSONObject? = null
            if (jsonResponse.startsWith("[")) {
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) targetJson = jsonArray.getJSONObject(0)
            } else if (jsonResponse.startsWith("{")) targetJson = JSONObject(jsonResponse)

            if (targetJson != null) {
                fun findKey(key: String): String {
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    val props = targetJson!!.optJSONObject("properties"); if (props != null && props.has(key)) return props.optString(key)
                    val features = targetJson!!.optJSONArray("features"); if (features != null && features.length() > 0) {
                        val firstFeature = features.getJSONObject(0)
                        val featProps = firstFeature.optJSONObject("properties"); if (featProps != null && featProps.has(key)) return featProps.optString(key)
                    }
                    return ""
                }
                val prov = findKey("provincia"); val mun = findKey("municipio"); val pol = findKey("poligono")
                val parc = findKey("parcela"); val rec = findKey("recinto"); val uso = findKey("uso_sigpac")
                if (prov.isNotEmpty() && mun.isNotEmpty()) return@withContext Pair("$prov:$mun:$pol:$parc:$rec", uso)
            }
        }
    } catch (e: Exception) {}
    return@withContext Pair(null, null)
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    folderName: String,
    sigpacRef: String,
    isProjectActive: Boolean,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val imageCapture = imageCapture ?: return
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val rotation = windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
    imageCapture.targetRotation = rotation

    val safeSigpacRef = sigpacRef.replace(":", "_")
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val filename = "${safeSigpacRef}-${timestamp}.jpg"
    val safeFolderName = folderName.replace("/", "-")
    
    val relativePath = if (isProjectActive) "DCIM/GeoSIGPAC/$safeFolderName/$safeSigpacRef" else "DCIM/GeoSIGPAC/SIN PROYECTO"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
    imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(exc: ImageCaptureException) { onError(exc) }
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            val savedUri = output.savedUri ?: Uri.EMPTY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != Uri.EMPTY) {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                try { context.contentResolver.update(savedUri, values, null, null) } catch (e: Exception) { e.printStackTrace() }
            }
            onImageCaptured(savedUri)
        }
    })
}
