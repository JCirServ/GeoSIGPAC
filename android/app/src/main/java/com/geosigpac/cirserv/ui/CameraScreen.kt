
package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.camera.*
import com.geosigpac.cirserv.ui.components.recinto.NativeRecintoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
    
    // --- CONFIGURACIÓN ---
    var photoFormat by remember { mutableStateOf(PhotoFormat.RATIO_4_3) }
    // 0:Auto, 1:On, 2:Off, 3:Torch
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) } 
    var gridMode by remember { mutableStateOf(GridMode.OFF) }
    var cameraQuality by remember { mutableStateOf(CameraQuality.MAX) }
    var overlayOptions by remember { mutableStateOf(setOf(OverlayOption.DATE, OverlayOption.COORDS, OverlayOption.REF, OverlayOption.PROJECT)) }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showParcelSheet by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    // CameraX Objects
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }
    var currentLinearZoom by remember { mutableFloatStateOf(0f) }

    // Logic State
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Data State
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }
    var isProcessingImage by remember { mutableStateOf(false) }

    // --- MATCHING LOGIC ---
    val matchedParcelInfo = remember(sigpacRef, expedientes) {
        if (sigpacRef == null) return@remember null
        var foundExp: NativeExpediente? = null
        val foundParcel = expedientes.flatMap { exp ->
            exp.parcelas.map { p -> 
                if (p.referencia == sigpacRef) {
                    foundExp = exp
                    p 
                } else null
            }
        }.filterNotNull().firstOrNull()
        if (foundParcel != null && foundExp != null) Pair(foundExp!!, foundParcel) else null
    }

    // --- PREVIEW BITMAP ---
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val targetPreviewUri = remember(matchedParcelInfo, lastCapturedUri) {
        if (matchedParcelInfo != null && matchedParcelInfo.second.photos.isNotEmpty()) {
            Uri.parse(matchedParcelInfo.second.photos.last())
        } else lastCapturedUri
    }
    val currentPhotoCount = remember(matchedParcelInfo, photoCount) {
        if (matchedParcelInfo != null) matchedParcelInfo.second.photos.size else photoCount
    }

    LaunchedEffect(targetPreviewUri) {
        targetPreviewUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    var finalBitmap = bitmap
                    // Rotar miniatura si es necesario
                    context.contentResolver.openInputStream(uri)?.use { exifInput ->
                         val exif = ExifInterface(exifInput)
                         val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                         val matrix = Matrix()
                         when (orientation) {
                             ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                             ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                             ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                         }
                         if (bitmap != null) {
                             finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                         }
                    }
                    capturedBitmap = finalBitmap?.asImageBitmap()
                } catch (e: Exception) { e.printStackTrace() }
            }
        } ?: run { capturedBitmap = null }
    }

    // Blink Animation
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label = "Blink"
    )

    // Zoom Observer
    LaunchedEffect(camera) {
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
            currentLinearZoom = state.linearZoom
        }
    }

    // --- CAMERA BINDING ---
    LaunchedEffect(photoFormat, flashMode, cameraQuality) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            // Determinar Aspect Ratio nativo para CameraX
            val cameraXRatio = when(photoFormat) {
                PhotoFormat.RATIO_16_9, PhotoFormat.FULL_SCREEN -> AspectRatio.RATIO_16_9
                else -> AspectRatio.RATIO_4_3 // 1:1 se captura como 4:3 y se recorta después
            }

            // Preview
            val preview = Preview.Builder().setTargetAspectRatio(cameraXRatio).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Capture
            val imageCaptureBuilder = ImageCapture.Builder().setTargetAspectRatio(cameraXRatio)

            when(cameraQuality) {
                CameraQuality.MAX -> imageCaptureBuilder.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                CameraQuality.HIGH -> imageCaptureBuilder.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                CameraQuality.BALANCED -> imageCaptureBuilder.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            }

            val captureFlashMode = if (flashMode == 3) ImageCapture.FLASH_MODE_OFF else flashMode
            imageCaptureBuilder.setFlashMode(captureFlashMode)

            val imageCapture = imageCaptureBuilder.build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                val cam = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                camera = cam
                imageCaptureUseCase = imageCapture
                
                // Torch
                if (flashMode == 3) cam.cameraControl.enableTorch(true) else cam.cameraControl.enableTorch(false)

            } catch (exc: Exception) { Log.e("CameraScreen", "Binding failed", exc) }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- GPS & SIGPAC LOOP ---
    LaunchedEffect(Unit) {
        while (true) {
            val loc = currentLocation
            val now = System.currentTimeMillis()
            if (loc != null) {
                val distance = if (lastApiLocation != null) loc.distanceTo(lastApiLocation!!) else Float.MAX_VALUE
                val timeElapsed = now - lastApiTimestamp
                if ((lastApiLocation == null || distance > 3.0f || timeElapsed > 5000) && !isLoadingSigpac) {
                    isLoadingSigpac = true
                    lastApiLocation = loc; lastApiTimestamp = now
                    try {
                        val (ref, uso) = CameraSigpacHelper.fetchRealSigpacData(loc.latitude, loc.longitude)
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
            override fun onLocationChanged(location: Location) {
                locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}"
                currentLocation = location
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { locationText = "Sin señal GPS" }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
            } catch (e: Exception) { locationText = "Error GPS" }
        }
        onDispose {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(listener)
            }
        }
    }
    
    // --- CAPTURE FUNCTION ---
    fun performCapture() {
        if (isProcessingImage) return
        
        scope.launch {
            isProcessingImage = true
            
            val cropToSquare = (photoFormat == PhotoFormat.RATIO_1_1)
            
            CameraCaptureLogic.takePhoto(
                context, imageCaptureUseCase, projectId, sigpacRef, currentLocation,
                cropToSquare = cropToSquare,
                overlayOptions = overlayOptions,
                onImageCaptured = { uri -> 
                    isProcessingImage = false
                    if (matchedParcelInfo != null) {
                        val (exp, parc) = matchedParcelInfo
                        val updatedParcela = parc.copy(photos = parc.photos + uri.toString())
                        val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })
                        onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
                    }
                    onImageCaptured(uri) 
                }, 
                onError = { isProcessingImage = false; onError(it) }
            )
        }
    }

    // --- UI COMPOSITION ---
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    camera?.let { cam ->
                        val currentRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        val newRatio = (currentRatio * zoom).coerceIn(cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f, cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                        cam.cameraControl.setZoomRatio(newRatio)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    showFocusRing = true; focusRingPosition = offset
                    scope.launch { delay(1000); showFocusRing = false }
                    camera?.let { cam ->
                        val point = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat()).createPoint(offset.x, offset.y)
                        cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                    }
                }
            }
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); scaleType = PreviewView.ScaleType.FILL_CENTER } })
        
        if (showFocusRing && focusRingPosition != null) {
            Box(modifier = Modifier.offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.x.toDp() - 25.dp }, y = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.y.toDp() - 25.dp }).size(50.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        }

        GridOverlay(gridMode)
        
        // MASK para 1:1 si está seleccionado
        if (photoFormat == PhotoFormat.RATIO_1_1) {
            Box(modifier = Modifier.fillMaxSize()) {
                val color = Color.Black.copy(0.7f)
                // Barras negras arriba y abajo para simular cuadrado
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(color).align(Alignment.TopCenter)) // Aproximación visual
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(color).align(Alignment.BottomCenter)) 
                // Nota: Una implementación perfecta usaría Canvas con drawRect calculando el centro, pero esto sirve de guía visual rápida.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = size.width
                    val topOffset = (size.height - side) / 2
                    // Top
                    drawRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, topOffset))
                    // Bottom
                    drawRect(color, Offset(0f, topOffset + side), androidx.compose.ui.geometry.Size(size.width, size.height - (topOffset + side)))
                }
            }
        }

        // --- CONTROLS UI ---
        if (isLandscape) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlButton(Icons.Default.Settings, "Config") { showSettingsDialog = true }
                    ControlButton(Icons.Default.List, "Proyectos") { onGoToProjects() }
                    if (matchedParcelInfo != null) ControlButton(Icons.Default.Info, "Info", alpha = blinkAlpha) { showParcelSheet = true }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                InfoBox(locationText, sigpacRef, sigpacUso, matchedParcelInfo, showNoDataMessage)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                ShutterButton(isProcessingImage) { performCapture() }
            }
            Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                SquareButton(Icons.Default.Image, "Preview", currentPhotoCount, capturedBitmap) {
                     if (matchedParcelInfo != null && matchedParcelInfo!!.second.photos.isNotEmpty()) showGallery = true else onClose()
                }
                SquareButton(MapIconVector, "Map") { onGoToMap() }
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 32.dp)) {
                ZoomControl(currentLinearZoom, true) { camera?.cameraControl?.setLinearZoom(it) }
            }
        } else {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlButton(Icons.Default.Settings, "Config") { showSettingsDialog = true }
                    ControlButton(Icons.Default.List, "Proyectos") { onGoToProjects() }
                    if (matchedParcelInfo != null) ControlButton(Icons.Default.Info, "Info", alpha = blinkAlpha) { showParcelSheet = true }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) {
                InfoBox(locationText, sigpacRef, sigpacUso, matchedParcelInfo, showNoDataMessage)
            }
            Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                ZoomControl(currentLinearZoom, false) { camera?.cameraControl?.setLinearZoom(it) }
            }
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    SquareButton(Icons.Default.Image, "Preview", currentPhotoCount, capturedBitmap) {
                         if (matchedParcelInfo != null && matchedParcelInfo!!.second.photos.isNotEmpty()) showGallery = true else onClose()
                    }
                    ShutterButton(isProcessingImage) { performCapture() }
                    SquareButton(MapIconVector, "Map") { onGoToMap() }
                }
            }
        }
        
        if (showSettingsDialog) {
            SettingsDialog(
                currentFormat = photoFormat,
                flashMode = flashMode,
                gridMode = gridMode,
                quality = cameraQuality,
                selectedOverlays = overlayOptions,
                onDismiss = { showSettingsDialog = false },
                onFormatChange = { photoFormat = it },
                onFlashChange = { flashMode = it },
                onGridChange = { gridMode = it },
                onQualityChange = { cameraQuality = it },
                onOverlayToggle = { opt ->
                    overlayOptions = if (overlayOptions.contains(opt)) overlayOptions - opt else overlayOptions + opt
                }
            )
        }
        
        if (showParcelSheet) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { showParcelSheet = false })
        }

        AnimatedVisibility(
            visible = showParcelSheet && matchedParcelInfo != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            matchedParcelInfo?.let { (exp, parc) ->
                 Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(0.4f))) }
                        NativeRecintoCard(parcela = parc, onLocate = { onGoToMap() }, onCamera = { showParcelSheet = false }, onUpdateParcela = { updatedParcela ->
                            val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })
                            onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
                        }, initiallyExpanded = true, initiallyTechExpanded = true)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
        
        if (showGallery && matchedParcelInfo != null) {
            val (exp, parc) = matchedParcelInfo!!
            FullScreenPhotoGallery(photos = parc.photos, initialIndex = parc.photos.lastIndex, onDismiss = { showGallery = false }, onDeletePhoto = { photoUriToDelete ->
                val updatedPhotos = parc.photos.filter { it != photoUriToDelete }
                val updatedParcela = parc.copy(photos = updatedPhotos)
                val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })
                onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
            })
        }
    }
}
