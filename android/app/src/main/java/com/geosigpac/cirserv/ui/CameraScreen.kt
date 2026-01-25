
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    var showGallery by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var showManualCaptureConfirmation by remember { mutableStateOf(false) }

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
    
    // --- RAY CASTING ---
    var geoMatchedParcel by remember { mutableStateOf<Pair<NativeExpediente, NativeParcela>?>(null) }

    LaunchedEffect(currentLocation, expedientes) {
        if (currentLocation == null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val lat = currentLocation!!.latitude
            val lng = currentLocation!!.longitude
            var match: Pair<NativeExpediente, NativeParcela>? = null
            for (exp in expedientes) {
                for (p in exp.parcelas) {
                    if (isLocationInsideParcel(lat, lng, p)) {
                        match = Pair(exp, p)
                        break
                    }
                }
                if (match != null) break
            }
            geoMatchedParcel = match
        }
    }

    val activeRef = manualSigpacRef ?: geoMatchedParcel?.second?.referencia ?: sigpacRef

    // --- MATCHING PRIORITY ---
    val matchedParcelInfo = remember(manualSigpacRef, geoMatchedParcel, sigpacRef, expedientes) {
        if (manualSigpacRef != null) {
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

    // --- THUMBNAIL LOGIC ---
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
            if (manualSigpacRef == null && loc != null) {
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

    // --- LOCATION UPDATES ---
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fun updateLocationUI(location: Location) {
            locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}"
            currentLocation = location
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(newLocation: Location) {
                val current = currentLocation
                var accept = false
                if (current == null) accept = true
                else {
                    if (newLocation.provider == LocationManager.GPS_PROVIDER) accept = true
                    else if (newLocation.provider == LocationManager.NETWORK_PROVIDER) {
                        if (current.provider == LocationManager.NETWORK_PROVIDER) accept = true
                        else if ((newLocation.time - current.time) > 120000) accept = true
                    }
                }
                if (accept) updateLocationUI(newLocation)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { if (currentLocation == null) locationText = "Sin señal GPS" }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLast = when {
                    lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                    lastGps != null -> lastGps
                    else -> lastNet
                }
                if (bestLast != null) updateLocationUI(bestLast)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
                try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, listener) } catch(_:Exception){}
            } catch (e: Exception) { locationText = "Error GPS" }
        }
        onDispose { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locationManager.removeUpdates(listener) }
    }

    // --- CAPTURE HANDLER ---
    fun performCapture() {
        if (isProcessingImage) return
        scope.launch {
            isProcessingImage = true
            val captureLocation = currentLocation
            val cropToSquare = (photoFormat == PhotoFormat.RATIO_1_1)
            val jpegQuality = when(cameraQuality) {
                CameraQuality.MAX -> 100; CameraQuality.HIGH -> 90; CameraQuality.MEDIUM -> 80; CameraQuality.LOW -> 60
            }
            val targetRef = matchedParcelInfo?.second?.referencia ?: activeRef
            val matchedExpedientes = if (targetRef != null) expedientes.filter { exp -> exp.parcelas.any { it.referencia == targetRef } } else emptyList()
            val projectNames = matchedExpedientes.map { it.titular }

            CameraCaptureLogic.takePhoto(
                context, imageCaptureUseCase, projectNames, targetRef, captureLocation,
                cropToSquare, jpegQuality, overlayOptions,
                onImageCaptured = { uriMap -> 
                    isProcessingImage = false
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
                    }
                    val previewUri = uriMap.values.firstOrNull()
                    if (previewUri != null) onImageCaptured(previewUri)
                }, 
                onError = { isProcessingImage = false; onError(it) }
            )
        }
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

        // 3. CONTROLS LAYER
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
            onShutterClick = { if (manualSigpacRef != null) showManualCaptureConfirmation = true else performCapture() },
            onPreviewClick = { if (matchedParcelInfo != null && matchedParcelInfo!!.second.photos.isNotEmpty()) showGallery = true else onClose() },
            onMapClick = onGoToMap,
            onZoomChange = { camera?.cameraControl?.setLinearZoom(it) }
        )

        // 4. DIALOGS & OVERLAYS
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
        
        if (showGallery && matchedParcelInfo != null) {
            val (exp, parc) = matchedParcelInfo!!
            FullScreenPhotoGallery(photos = parc.photos, initialIndex = parc.photos.lastIndex, onDismiss = { showGallery = false }, onDeletePhoto = { photoUriToDelete ->
                val updatedPhotos = parc.photos.filter { it != photoUriToDelete }
                val updatedLocs = parc.photoLocations.toMutableMap().apply { remove(photoUriToDelete) }
                val updatedParcel = parc.copy(photos = updatedPhotos, photoLocations = updatedLocs)
                val updatedExp = exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcel.id) updatedParcel else it })
                onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
            })
        }
    }
}

// --- HELPER LOGIC ---
private fun isLocationInsideParcel(lat: Double, lng: Double, parcela: NativeParcela): Boolean {
    val geomRaw = parcela.geometryRaw
    if (geomRaw.isNullOrEmpty()) return false
    try {
        if (geomRaw.trim().startsWith("{")) {
            val jsonObject = JSONObject(geomRaw)
            val type = jsonObject.optString("type")
            val coordinates = jsonObject.getJSONArray("coordinates")
            if (type.equals("Polygon", ignoreCase = true)) {
                if (coordinates.length() > 0) return isPointInPolygon(lat, lng, parseJsonRing(coordinates.getJSONArray(0)))
            } else if (type.equals("MultiPolygon", ignoreCase = true)) {
                for (i in 0 until coordinates.length()) {
                    if (isPointInPolygon(lat, lng, parseJsonRing(coordinates.getJSONArray(i).getJSONArray(0)))) return true
                }
            }
            return false
        } else {
            val points = mutableListOf<Pair<Double, Double>>()
            val coordPairs = geomRaw.trim().split("\\s+".toRegex())
            for (pair in coordPairs) {
                val coords = pair.split(",")
                if (coords.size >= 2) {
                    val pLng = coords[0].toDoubleOrNull(); val pLat = coords[1].toDoubleOrNull()
                    if (pLng != null && pLat != null) points.add(pLat to pLng)
                }
            }
            return if (points.size >= 3) isPointInPolygon(lat, lng, points) else false
        }
    } catch (e: Exception) { return false }
}

private fun parseJsonRing(jsonRing: JSONArray): List<Pair<Double, Double>> {
    val list = mutableListOf<Pair<Double, Double>>()
    for (i in 0 until jsonRing.length()) {
        val pt = jsonRing.getJSONArray(i)
        list.add(pt.getDouble(1) to pt.getDouble(0))
    }
    return list
}

private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val (latI, lngI) = polygon[i]
        val (latJ, lngJ) = polygon[j]
        if (((latI > lat) != (latJ > lat)) && (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)) inside = !inside
        j = i
    }
    return inside
}
