
package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.ui.camera.*
import com.geosigpac.cirserv.ui.components.recinto.NativeRecintoCard
import com.geosigpac.cirserv.utils.CameraSettings
import com.geosigpac.cirserv.utils.CameraSettingsStorage
import com.geosigpac.cirserv.utils.SpatialIndex
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
    
    // --- OPTIMIZACIÓN ESPACIAL ---
    val spatialIndex = remember { SpatialIndex() }
    
    LaunchedEffect(expedientes) {
        spatialIndex.rebuild(expedientes)
    }
    
    // --- SETTINGS ---
    val initialSettings = remember { CameraSettingsStorage.loadSettings(context) }
    var photoFormat by remember { mutableStateOf(initialSettings.photoFormat) }
    var flashMode by remember { mutableIntStateOf(initialSettings.flashMode) } 
    var gridMode by remember { mutableStateOf(initialSettings.gridMode) }
    var cameraQuality by remember { mutableStateOf(initialSettings.cameraQuality) }
    var overlayOptions by remember { mutableStateOf(initialSettings.overlayOptions) }
    
    LaunchedEffect(photoFormat, flashMode, gridMode, cameraQuality, overlayOptions) {
        CameraSettingsStorage.saveSettings(context, CameraSettings(photoFormat, flashMode, gridMode, cameraQuality, overlayOptions))
    }
    
    // --- UI STATES ---
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showParcelSheet by remember { mutableStateOf(false) }
    
    // GALERÍA STATE
    var showGallery by remember { mutableStateOf(false) }
    var galleryTarget by remember { mutableStateOf<Pair<NativeExpediente, NativeParcela>?>(null) }
    
    var showManualInput by remember { mutableStateOf(false) }
    var showManualCaptureConfirmation by remember { mutableStateOf(false) }
    
    // --- NEAREST PARCEL ALERT STATE ---
    var showNearestDialog by remember { mutableStateOf(false) }
    var nearestCandidate by remember { mutableStateOf<Pair<NativeParcela, Float>?>(null) }

    // --- CAMERA X ---
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }
    var currentLinearZoom by remember { mutableFloatStateOf(0f) }
    
    // --- LOGIC STATES ---
    var manualSigpacRef by remember { mutableStateOf<String?>(null) }
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }
    var isProcessingImage by remember { mutableStateOf(false) }

    // --- DATA & LOCATION ---
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }
    
    // --- RAY CASTING (Optimized) ---
    var geoMatchedParcel by remember { mutableStateOf<Pair<NativeExpediente, NativeParcela>?>(null) }

    // Optimización: Usamos el SpatialIndex en lugar del bucle lineal
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        // Esta operación ahora es < 5ms gracias al R-Tree y corre en Default Dispatcher
        geoMatchedParcel = spatialIndex.findContainingParcel(loc.latitude, loc.longitude)
    }

    val activeRef = manualSigpacRef ?: geoMatchedParcel?.second?.referencia ?: sigpacRef

    // --- MATCHING PRIORITY & DATA RESOLUTION ---
    val generalExp = remember(expedientes) { expedientes.find { it.id == "EXP_GENERAL_NO_PROJECT" } }
    val generalParcel = remember(generalExp) { generalExp?.parcelas?.firstOrNull() }

    val matchedParcelInfo = remember(projectId, manualSigpacRef, geoMatchedParcel, sigpacRef, expedientes) {
        if (projectId != null) {
            var foundExp: NativeExpediente? = null
            val foundParcel = expedientes.flatMap { exp ->
                exp.parcelas.map { p -> if (p.id == projectId) { foundExp = exp; p } else null }
            }.filterNotNull().firstOrNull()
            
            if (foundParcel != null && foundExp != null) {
                Pair(foundExp!!, foundParcel)
            } else {
                null
            }
        } else if (manualSigpacRef != null) {
            var foundExp: NativeExpediente? = null
            val foundParcel = expedientes.flatMap { exp ->
                exp.parcelas.map { p -> if (p.referencia == manualSigpacRef) { foundExp = exp; p } else null }
            }.filterNotNull().firstOrNull()
            if (foundParcel != null && foundExp != null) Pair(foundExp!!, foundParcel) else null
        } else if (geoMatchedParcel != null) {
            geoMatchedParcel
        } else if (sigpacRef != null) {
            var foundExp: NativeExpediente? = null
            val foundParcel = expedientes.flatMap { exp ->
                exp.parcelas.map { p -> if (p.referencia == sigpacRef) { foundExp = exp; p } else null }
            }.filterNotNull().firstOrNull()
            if (foundParcel != null && foundExp != null) Pair(foundExp!!, foundParcel) else null
        } else {
            null
        }
    }

    val activeDisplayParcel = matchedParcelInfo?.second ?: generalParcel
    val activeDisplayExp = matchedParcelInfo?.first ?: generalExp

    // --- THUMBNAIL LOGIC ---
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val targetPreviewUri = remember(activeDisplayParcel, lastCapturedUri) {
        if (activeDisplayParcel != null && activeDisplayParcel.photos.isNotEmpty()) {
            Uri.parse(activeDisplayParcel.photos.last())
        } else lastCapturedUri
    }
    
    val currentPhotoCount = remember(activeDisplayParcel, photoCount) {
        if (activeDisplayParcel != null) activeDisplayParcel.photos.size else photoCount
    }

    LaunchedEffect(targetPreviewUri) {
        targetPreviewUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    var finalBitmap = bitmap
                    context.contentResolver.openInputStream(uri)?.use { exifInput ->
                         val exif = ExifInterface(exifInput)
                         val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                         val matrix = Matrix()
                         when (orientation) {
                             ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                             ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                             ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                         }
                         if (bitmap != null) finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }
                    capturedBitmap = finalBitmap?.asImageBitmap()
                } catch (e: Exception) { e.printStackTrace() }
            }
        } ?: run { capturedBitmap = null }
    }

    // --- CAMERA LIFECYCLE ---
    LaunchedEffect(camera) {
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state -> currentLinearZoom = state.linearZoom }
    }

    LaunchedEffect(photoFormat, flashMode, cameraQuality) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            val cameraXRatio = when(photoFormat) {
                PhotoFormat.RATIO_16_9, PhotoFormat.FULL_SCREEN -> AspectRatio.RATIO_16_9
                else -> AspectRatio.RATIO_4_3 
            }
            val preview = Preview.Builder().setTargetAspectRatio(cameraXRatio).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val imageCaptureBuilder = ImageCapture.Builder().setTargetAspectRatio(cameraXRatio)
            val captureMode = when(cameraQuality) {
                CameraQuality.MAX, CameraQuality.HIGH -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            }
            imageCaptureBuilder.setCaptureMode(captureMode)
            imageCaptureBuilder.setFlashMode(if (flashMode == 3) ImageCapture.FLASH_MODE_OFF else flashMode)
            val imageCapture = imageCaptureBuilder.build()
            try {
                val cam = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                camera = cam
                imageCaptureUseCase = imageCapture
                if (flashMode == 3) cam.cameraControl.enableTorch(true) else cam.cameraControl.enableTorch(false)
            } catch (exc: Exception) { Log.e("CameraScreen", "Binding failed", exc) }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- API POLLING ---
    LaunchedEffect(Unit) {
        while (true) {
            val loc = currentLocation
            val now = System.currentTimeMillis()
            if (projectId == null && manualSigpacRef == null && loc != null) {
                val distance = if (lastApiLocation != null) loc.distanceTo(lastApiLocation!!) else Float.MAX_VALUE
                if ((lastApiLocation == null || distance > 5.0f || (now - lastApiTimestamp) > 5000) && !isLoadingSigpac) {
                    isLoadingSigpac = true
                    lastApiLocation = loc; lastApiTimestamp = now
                    try {
                        val (ref, uso) = CameraSigpacHelper.fetchRealSigpacData(loc.latitude, loc.longitude)
                        if (ref != null) { sigpacRef = ref; sigpacUso = uso; showNoDataMessage = false } 
                        else { sigpacRef = null; sigpacUso = null; delay(2000); if (sigpacRef == null) showNoDataMessage = true }
                    } catch (e: Exception) { } finally { isLoadingSigpac = false }
                }
            }
            delay(1000)
        }
    }

    // --- LOCATION UPDATES (FUSED LOCATION PROVIDER) ---
    DisposableEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(1000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}\nPrecisión: ${location.accuracy.toInt()}m"
                }
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLocation = loc
                        locationText = "Lat: ${String.format("%.6f", loc.latitude)}\nLng: ${String.format("%.6f", loc.longitude)}"
                    }
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: Exception) { locationText = "Error GPS" }
        } else {
            locationText = "Sin permisos GPS"
        }

        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    // --- CAPTURE HANDLER ---
    fun performCapture(forcedParcelOverride: NativeParcela? = null) {
        if (isProcessingImage) return
        scope.launch {
            isProcessingImage = true
            val captureLocation = currentLocation
            val cropToSquare = (photoFormat == PhotoFormat.RATIO_1_1)
            val jpegQuality = when(cameraQuality) {
                CameraQuality.MAX -> 100; CameraQuality.HIGH -> 90; CameraQuality.MEDIUM -> 80; CameraQuality.LOW -> 60
            }
            
            val targetRef = forcedParcelOverride?.referencia 
                ?: matchedParcelInfo?.second?.referencia 
                ?: activeRef
            
            val matchedExpedientes = if (targetRef != null) {
                expedientes.filter { exp -> exp.parcelas.any { it.referencia == targetRef } }
            } else emptyList()
            
            val projectNames = matchedExpedientes.map { it.titular }

            CameraCaptureLogic.takePhoto(
                context, imageCaptureUseCase, projectNames, targetRef, captureLocation,
                cropToSquare, jpegQuality, overlayOptions,
                onImageCaptured = { uriMap -> 
                    isProcessingImage = false
                    val previewUri = uriMap.values.firstOrNull()

                    if (matchedExpedientes.isNotEmpty() && targetRef != null) {
                        val newExpedientes = expedientes.map { exp ->
                            val projectUri = uriMap[exp.titular]
                            if (projectUri != null && exp.parcelas.any { it.referencia == targetRef }) {
                                exp.copy(parcelas = exp.parcelas.map { p ->
                                    if (p.referencia == targetRef) {
                                        val newPhotos = p.photos + projectUri.toString()
                                        val newLocations = if (captureLocation != null) p.photoLocations + (projectUri.toString() to "${captureLocation.latitude},${captureLocation.longitude}") else p.photoLocations
                                        p.copy(photos = newPhotos, photoLocations = newLocations)
                                    } else p
                                })
                            } else exp
                        }
                        onUpdateExpedientes(newExpedientes)
                    } else {
                        if (previewUri != null) {
                            val generalExpId = "EXP_GENERAL_NO_PROJECT"
                            val generalParcelId = "PARCEL_GENERAL_NO_PROJECT"
                            val existingGeneralExp = expedientes.find { it.id == generalExpId }
                            val updatedExp = if (existingGeneralExp != null) {
                                val p = existingGeneralExp.parcelas.firstOrNull() ?: NativeParcela(id = generalParcelId, referencia = "FOTOS SIN PROYECTO", uso = "GEN", lat = 0.0, lng = 0.0, area = 0.0, metadata = emptyMap())
                                val newPhotos = p.photos + previewUri.toString()
                                val newLocs = if (captureLocation != null) p.photoLocations + (previewUri.toString() to "${captureLocation.latitude},${captureLocation.longitude}") else p.photoLocations
                                existingGeneralExp.copy(parcelas = listOf(p.copy(photos = newPhotos, photoLocations = newLocs)))
                            } else {
                                val p = NativeParcela(id = generalParcelId, referencia = "FOTOS SIN PROYECTO", uso = "GEN", lat = captureLocation?.latitude ?: 0.0, lng = captureLocation?.longitude ?: 0.0, area = 0.0, metadata = emptyMap(), photos = listOf(previewUri.toString()), photoLocations = if (captureLocation != null) mapOf(previewUri.toString() to "${captureLocation.latitude},${captureLocation.longitude}") else emptyMap())
                                NativeExpediente(id = generalExpId, titular = "Sin Proyecto Asignado", fechaImportacion = "General", parcelas = listOf(p))
                            }
                            val newList = if (existingGeneralExp != null) expedientes.map { if (it.id == generalExpId) updatedExp else it } else expedientes + updatedExp
                            onUpdateExpedientes(newList)
                        }
                    }
                    if (previewUri != null) onImageCaptured(previewUri)
                }, 
                onError = { isProcessingImage = false; onError(it) }
            )
        }
    }

    // --- NEAREST PARCEL LOGIC ---
    fun findNearestParcel(): Pair<NativeParcela, Float>? {
        val currentLoc = currentLocation ?: return null
        var minDistance = Float.MAX_VALUE
        var nearest: NativeParcela? = null

        expedientes.forEach { exp ->
            exp.parcelas.forEach { p ->
                val pLat = p.centroidLat
                val pLng = p.centroidLng
                if (pLat != null && pLng != null && pLat != 0.0) {
                    val results = FloatArray(1)
                    Location.distanceBetween(currentLoc.latitude, currentLoc.longitude, pLat, pLng, results)
                    val dist = results[0]
                    if (dist < minDistance) {
                        minDistance = dist
                        nearest = p
                    }
                }
            }
        }
        return if (nearest != null) Pair(nearest!!, minDistance) else null
    }

    // --- UI COMPOSITION ---
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. PREVIEW LAYER
        CameraPreview(
            previewView = previewView,
            photoFormat = photoFormat,
            gridMode = gridMode,
            showFocusRing = showFocusRing,
            focusRingPosition = focusRingPosition,
            onTapToFocus = { offset ->
                showFocusRing = true; focusRingPosition = offset
                scope.launch { delay(1000); showFocusRing = false }
                camera?.let { cam ->
                    val point = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat()).createPoint(offset.x, offset.y)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                }
            },
            onZoomGesture = { zoomDelta ->
                camera?.let { cam ->
                    val currentRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val newRatio = (currentRatio * zoomDelta).coerceIn(cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f, cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                    cam.cameraControl.setZoomRatio(newRatio)
                }
            }
        )

        // 2. LOCATION OVERLAY
        LocationOverlay(
            isLandscape = isLandscape,
            locationText = locationText,
            activeRef = activeRef,
            sigpacUso = sigpacUso,
            matchedParcelInfo = matchedParcelInfo,
            showNoDataMessage = showNoDataMessage,
            onClearManual = if (manualSigpacRef != null) { { manualSigpacRef = null } } else null
        )
        
        // 3. MINI MAPA (Nuevo componente)
        // Posicionado en la esquina superior izquierda (debajo de los controles de configuración si hay conflicto, 
        // pero vamos a ponerlo arriba y ajustar el padding de los controles)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp) // Alineado con los controles si es portrait, pero lo desplazamos
        ) {
            // Nota: En portrait los controles están Top-Start. Si ponemos el mapa aquí, tapará los botones.
            // Vamos a poner el mapa DEBAJO de los botones en Portrait, o mover los botones.
            // Mejor opción: Mapa en la parte inferior derecha, encima del Zoom? O Inferior Izquierda.
            // Vamos a ponerlo en Top-Start pero desplazado hacia abajo si estamos en Portrait.
            
            // Layout Decision: 
            // - Portrait: Mapa Top-Left, debajo de botones Settings/List/Manual.
            // - Landscape: Mapa Bottom-Left, encima de botones Preview.
            
            val mapModifier = if (isLandscape) {
                Modifier.align(Alignment.BottomStart).padding(bottom = 100.dp) 
            } else {
                Modifier.padding(top = 180.dp) // Dejar espacio para los 3 botones de control (aprox 3 * 50dp + spacing)
            }
            
            MiniMapOverlay(
                modifier = mapModifier,
                userLocation = currentLocation,
                expedientes = expedientes
            )
        }

        // 4. CONTROLS LAYER
        CameraControls(
            isLandscape = isLandscape,
            isProcessingImage = isProcessingImage,
            currentZoom = currentLinearZoom,
            matchedParcelInfo = matchedParcelInfo,
            isInsideGeometry = geoMatchedParcel != null,
            photoCount = currentPhotoCount, 
            capturedBitmap = capturedBitmap,
            onSettingsClick = { showSettingsDialog = true },
            onProjectsClick = onGoToProjects,
            onManualClick = { showManualInput = true },
            onInfoClick = { showParcelSheet = true },
            onShutterClick = { 
                if (manualSigpacRef != null) {
                    showManualCaptureConfirmation = true 
                } else if (matchedParcelInfo == null) {
                    val nearest = findNearestParcel()
                    if (nearest != null) {
                        nearestCandidate = nearest
                        showNearestDialog = true
                    } else {
                        performCapture()
                    }
                } else {
                    performCapture() 
                }
            },
            onPreviewClick = { 
                if (activeDisplayParcel != null && activeDisplayExp != null && activeDisplayParcel.photos.isNotEmpty()) {
                    galleryTarget = Pair(activeDisplayExp, activeDisplayParcel)
                    showGallery = true
                } else {
                    onClose()
                }
            },
            onMapClick = onGoToMap,
            onZoomChange = { camera?.cameraControl?.setLinearZoom(it) }
        )

        // 5. DIALOGS & OVERLAYS
        if (showSettingsDialog) {
            SettingsDialog(
                photoFormat, flashMode, gridMode, cameraQuality, overlayOptions,
                onDismiss = { showSettingsDialog = false },
                onFormatChange = { photoFormat = it }, onFlashChange = { flashMode = it },
                onGridChange = { gridMode = it }, onQualityChange = { cameraQuality = it },
                onOverlayToggle = { opt -> overlayOptions = if (overlayOptions.contains(opt)) overlayOptions - opt else overlayOptions + opt }
            )
        }

        if (showManualCaptureConfirmation) {
            AlertDialog(
                containerColor = Color.Black.copy(0.9f),
                titleContentColor = NeonGreen, textContentColor = Color.White,
                onDismissRequest = { showManualCaptureConfirmation = false },
                icon = { Icon(Icons.Default.Warning, null, tint = Color.Yellow) },
                title = { Text("Referencia Manual Activa") },
                text = { Column { Text("Referencia fija ignorando GPS:"); Spacer(Modifier.height(8.dp)); Text(manualSigpacRef ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace); Spacer(Modifier.height(8.dp)); Text("¿Guardar foto asociada?") } },
                confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = NeonGreen), onClick = { showManualCaptureConfirmation = false; performCapture() }) { Text("Confirmar", color = Color.Black) } },
                dismissButton = { TextButton(onClick = { showManualCaptureConfirmation = false }) { Text("Cancelar", color = Color.Gray) } }
            )
        }

        if (showNearestDialog && nearestCandidate != null) {
            val (parcel, dist) = nearestCandidate!!
            AlertDialog(
                containerColor = Color.Black.copy(0.9f),
                titleContentColor = NeonGreen, textContentColor = Color.White,
                onDismissRequest = { showNearestDialog = false },
                icon = { Icon(Icons.Default.NearMe, null, tint = Color.Cyan) },
                title = { Text("Fuera de Recinto") },
                text = { 
                    Column { 
                        Text("No estás ubicado dentro de ningún recinto del proyecto.")
                        Spacer(Modifier.height(12.dp))
                        Text("Más cercano encontrado:", fontSize = 12.sp, color = Color.Gray)
                        Text(parcel.referencia, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Text("Distancia: ${dist.roundToInt()} metros", color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("¿Quieres asignar la foto a este recinto cercano?") 
                    } 
                },
                confirmButton = { 
                    Button(colors = ButtonDefaults.buttonColors(containerColor = NeonGreen), onClick = { showNearestDialog = false; performCapture(forcedParcelOverride = parcel) }) { Text("Sí, Asignar", color = Color.Black) } 
                },
                dismissButton = { 
                    TextButton(onClick = { showNearestDialog = false; performCapture() }) { Text("Guardar en 'Sin Proyecto'", color = Color.White) }
                }
            )
        }

        AnimatedVisibility(visible = showManualInput, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            CameraSigpacKeyboard(currentValue = manualSigpacRef ?: "", onValueChange = { manualSigpacRef = it }, onConfirm = { showManualInput = false }, onClose = { if (manualSigpacRef.isNullOrEmpty()) manualSigpacRef = null; showManualInput = false })
        }
        
        if (showParcelSheet) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { showParcelSheet = false })

        AnimatedVisibility(visible = showParcelSheet && matchedParcelInfo != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
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
        
        if (showGallery && galleryTarget != null) {
            val (targetExp, targetParc) = galleryTarget!!
            FullScreenPhotoGallery(
                photos = targetParc.photos, 
                initialIndex = targetParc.photos.lastIndex, 
                onDismiss = { showGallery = false }, 
                onDeletePhoto = { photoUriToDelete ->
                    val updatedPhotos = targetParc.photos.filter { it != photoUriToDelete }
                    val updatedLocs = targetParc.photoLocations.toMutableMap().apply { remove(photoUriToDelete) }
                    val updatedParcel = targetParc.copy(photos = updatedPhotos, photoLocations = updatedLocs)
                    
                    val updatedExp = targetExp.copy(parcelas = targetExp.parcelas.map { if (it.id == updatedParcel.id) updatedParcel else it })
                    val newList = expedientes.map { if (it.id == updatedExp.id) updatedExp else it }
                    onUpdateExpedientes(newList)
                    
                    if (updatedPhotos.isEmpty()) { showGallery = false } else { galleryTarget = Pair(updatedExp, updatedParcel) }
                }
            )
        }
    }
}
