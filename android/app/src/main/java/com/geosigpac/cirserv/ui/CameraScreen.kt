package com.geosigpac.cirserv.ui

import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.*
import android.Manifest
import android.content.ContentValues
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
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.camera.core.*
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
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.ui.components.recinto.NativeRecintoCard
import com.geosigpac.cirserv.utils.BatteryOptimizer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.TransformOrigin

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
    
    var aspectRatio by remember { mutableIntStateOf(AspectRatio.RATIO_4_3) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var showGrid by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showParcelSheet by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }

    var currentLinearZoom by remember { mutableFloatStateOf(0f) }
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }

    val matchedParcelInfo = remember(sigpacRef, expedientes) {
        if (sigpacRef == null) return@remember null
        var foundExp: NativeExpediente? = null
        val foundParcel = expedientes.flatMap { exp ->
            exp.parcelas.map { p -> if (p.referencia == sigpacRef) { foundExp = exp; p } else null }
        }.filterNotNull().firstOrNull()
        if (foundParcel != null && foundExp != null) Pair(foundExp!!, foundParcel) else null
    }

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
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        
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
                        if (bitmap != null) {
                            finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                    }
                    capturedBitmap = finalBitmap?.asImageBitmap()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alpha"
    )
    
    val MapIcon = remember {
        ImageVector.Builder(name = "Map", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20.5f, 3.0f); lineTo(20.34f, 3.03f); lineTo(15.0f, 5.1f); lineTo(9.0f, 3.0f); lineTo(3.36f, 4.9f)
                curveTo(3.15f, 4.97f, 3.0f, 5.15f, 3.0f, 5.38f); verticalLineTo(20.5f); curveTo(3.0f, 20.78f, 3.22f, 21.0f, 3.5f, 21.0f)
                lineTo(3.66f, 20.97f); lineTo(9.0f, 18.9f); lineTo(15.0f, 21.0f); lineTo(20.64f, 19.1f); curveTo(20.85f, 19.03f, 21.0f, 18.85f, 21.0f, 18.62f)
                verticalLineTo(3.5f); curveTo(21.0f, 3.22f, 20.78f, 3.0f, 20.5f, 3.0f); close()
                moveTo(15.0f, 19.0f); lineTo(9.0f, 16.89f); verticalLineTo(5.0f); lineTo(15.0f, 7.11f); verticalLineTo(19.0f); close()
            }
        }.build()
    }

    LaunchedEffect(camera) {
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
            currentLinearZoom = state.linearZoom
        }
    }

    LaunchedEffect(aspectRatio, flashMode) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider.unbindAll()
        val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(aspectRatio)
            .setFlashMode(flashMode)
            .build()
        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            imageCaptureUseCase = imageCapture
        } catch (exc: Exception) { Log.e("CameraScreen", "Binding failed", exc) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val loc = currentLocation
            val now = System.currentTimeMillis()
            if (loc != null) {
                val distance = if (lastApiLocation != null) loc.distanceTo(lastApiLocation!!) else Float.MAX_VALUE
                if ((lastApiLocation == null || distance > 3.0f || (now - lastApiTimestamp) > 5000) && !isLoadingSigpac) {
                    isLoadingSigpac = true
                    try {
                        val (ref, uso) = fetchRealSigpacData(loc.latitude, loc.longitude)
                        sigpacRef = ref; sigpacUso = uso; showNoDataMessage = ref == null
                        lastApiLocation = loc; lastApiTimestamp = now
                    } catch (e: Exception) { } finally { isLoadingSigpac = false }
                }
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        BatteryOptimizer.acquireWakeLock(context, "GeoSIGPAC:Camera")
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                locationText = "Lat: ${String.format("%.6f", l.latitude)}\nLng: ${String.format("%.6f", l.longitude)}"
                currentLocation = l
            }
            override fun onProviderDisabled(p: String) { locationText = "Sin señal GPS" }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, BatteryOptimizer.getOptimalGPSInterval(context), 5f, listener)
        }
        onDispose { locationManager.removeUpdates(listener); BatteryOptimizer.releaseWakeLock() }
    }

    val NeonGreen = Color(0xFF00FF88)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
                camera?.let { cam ->
                    val currentRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    cam.cameraControl.setZoomRatio(currentRatio * zoom)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                focusRingPosition = offset; showFocusRing = true
                scope.launch { delay(1000); showFocusRing = false }
                camera?.let { cam ->
                    val point = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat()).createPoint(offset.x, offset.y)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                }
            }
        }
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        
        if (showFocusRing && focusRingPosition != null) {
            Box(modifier = Modifier.offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.x.toDp() - 25.dp }, y = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.y.toDp() - 25.dp }).size(50.dp).border(2.dp, Color.White.copy(0.8f), CircleShape))
        }

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(Color.White.copy(0.3f), Offset(size.width/3, 0f), Offset(size.width/3, size.height), 2f)
                drawLine(Color.White.copy(0.3f), Offset(2*size.width/3, 0f), Offset(2*size.width/3, size.height), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, size.height/3), Offset(size.width, size.height/3), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, 2*size.height/3), Offset(size.width, 2*size.height/3), 2f)
            }
        }

        if (isLandscape) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, null, tint = NeonGreen) }
                    IconButton(onClick = onGoToProjects) { Icon(Icons.Default.List, null, tint = NeonGreen) }
                    if (matchedParcelInfo != null) IconButton(onClick = { showParcelSheet = true }) { Icon(Icons.Default.Info, null, tint = NeonGreen.copy(alpha = blinkAlpha)) }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { 
                InfoBoxContent(locationText, sigpacRef, sigpacUso, showNoDataMessage, matchedParcelInfo != null, NeonGreen) 
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) { 
                ShutterButtonContent(context, imageCaptureUseCase, projectId, sigpacRef, scope, matchedParcelInfo, expedientes, onUpdateExpedientes, onImageCaptured, onError, NeonGreen) 
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp, start = 16.dp, end = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, null, tint = NeonGreen) }
                        IconButton(onClick = onGoToProjects) { Icon(Icons.Default.List, null, tint = NeonGreen) }
                        if (matchedParcelInfo != null) IconButton(onClick = { showParcelSheet = true }) { Icon(Icons.Default.Info, null, tint = NeonGreen.copy(alpha = blinkAlpha)) }
                    }
                    InfoBoxContent(locationText, sigpacRef, sigpacUso, showNoDataMessage, matchedParcelInfo != null, NeonGreen)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PreviewButtonContent(capturedBitmap, currentPhotoCount, matchedParcelInfo, { showGallery = true }, onClose, NeonGreen)
                    ShutterButtonContent(context, imageCaptureUseCase, projectId, sigpacRef, scope, matchedParcelInfo, expedientes, onUpdateExpedientes, onImageCaptured, onError, NeonGreen)
                    IconButton(onClick = onGoToMap, modifier = Modifier.size(80.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(24.dp)).border(2.dp, NeonGreen, RoundedCornerShape(24.dp))) { Icon(MapIcon, null, tint = NeonGreen) }
                }
            }
        }
        
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configuración") },
                text = {
                    Column {
                        Text("Aspect Ratio"); Row {
                            RadioButton(aspectRatio == AspectRatio.RATIO_4_3, { aspectRatio = AspectRatio.RATIO_4_3 }); Text("4:3")
                            RadioButton(aspectRatio == AspectRatio.RATIO_16_9, { aspectRatio = AspectRatio.RATIO_16_9 }); Text("16:9")
                        }
                        Checkbox(showGrid, { showGrid = it }); Text("Cuadrícula")
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("OK") } }
            )
        }

        if (showParcelSheet && matchedParcelInfo != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable { showParcelSheet = false })
            AnimatedVisibility(visible = true, modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        NativeRecintoCard(parcela = matchedParcelInfo.second, onLocate = { onGoToMap() }, onCamera = { showParcelSheet = false }, onUpdateParcela = { }, initiallyExpanded = true)
                        Button(onClick = { showParcelSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
                    }
                }
            }
        }

        if (showGallery && matchedParcelInfo != null) {
            FullScreenPhotoGallery(photos = matchedParcelInfo.second.photos, onDismiss = { showGallery = false })
        }
    }
}

@Composable
fun InfoBoxContent(loc: String, ref: String?, uso: String?, noData: Boolean, matched: Boolean, neon: Color) {
    Box(modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text(loc, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            ref?.let { 
                Text("Ref: $it", color = Color.Yellow, fontWeight = FontWeight.Bold)
                Text("Uso: ${uso ?: "N/D"}", color = Color.White)
                if (matched) Text("EN PROYECTO", color = neon, fontWeight = FontWeight.Black, fontSize = 10.sp)
            } ?: if (noData) Text("Sin datos SIGPAC", color = Color.Red)
        }
    }
}

@Composable
fun ShutterButtonContent(ctx: Context, ic: ImageCapture?, pid: String?, ref: String?, scope: CoroutineScope, matched: Pair<NativeExpediente, NativeParcela>?, exps: List<NativeExpediente>, onUpdate: (List<NativeExpediente>) -> Unit, onCap: (Uri) -> Unit, onErr: (ImageCaptureException) -> Unit, neon: Color) {
    Box(modifier = Modifier.size(80.dp).border(4.dp, neon, CircleShape).padding(6.dp).background(neon, CircleShape).clickable {
        takePhoto(ctx, ic, pid, ref, scope, { uri ->
            matched?.let { (exp, parc) ->
                val updatedParc = parc.copy(photos = parc.photos + uri.toString())
                onUpdate(exps.map { if (it.id == exp.id) exp.copy(parcelas = exp.parcelas.map { p -> if (p.id == updatedParc.id) updatedParc else p }) else it })
            }
            onCap(uri)
        }, onErr)
    })
}

@Composable
fun PreviewButtonContent(bmp: ImageBitmap?, count: Int, matched: Any?, onGal: () -> Unit, onCl: () -> Unit, neon: Color) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(0.5f))
            .border(2.dp, neon, RoundedCornerShape(24.dp))
            .clickable { 
                // CORRECCIÓN LÍNEA 382: If como expresión requiere else
                if (matched != null) {
                    onGal()
                } else {
                    onCl()
                }
            }
        ) {
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Image, null, tint = neon, modifier = Modifier.align(Alignment.Center))
        }
        if (count > 0) Box(modifier = Modifier.offset(x = 4.dp, y = (-4).dp).size(24.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) { Text(count.toString(), color = Color.White, fontSize = 10.sp) }
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture?, projectId: String?, sigpacRef: String?, scope: CoroutineScope, onImageCaptured: (Uri) -> Unit, onError: (ImageCaptureException) -> Unit) {
    val ic = imageCapture ?: return
    val name = "SIGPAC_${sigpacRef?.replace(":", "_") ?: "IMG"}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/GeoSIGPAC/${projectId ?: "General"}")
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
    ic.takePicture(outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(o: ImageCapture.OutputFileResults) {
            val uri = o.savedUri ?: Uri.EMPTY
            scope.launch { try { compressPhoto(context, uri) } catch (e: Exception) { } }
            onImageCaptured(uri)
        }
        override fun onError(e: ImageCaptureException) { onError(e) }
    })
}

private suspend fun compressPhoto(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
    Compressor.compress(context, tempFile) { quality(80); resolution(1920, 1080) }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (h, w) = options.outHeight to options.outWidth
    var s = 1
    if (h > reqHeight || w > reqWidth) {
        val (hh, hw) = h / 2 to w / 2
        while (hh / s >= reqHeight && hw / s >= reqWidth) s *= 2
    }
    return s
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val conn = URL("https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/$lng/$lat.json").openConnection() as HttpURLConnection
        if (conn.responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(jsonText)
            val p = json.optJSONObject("properties") ?: json.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties")
            if (p != null) return@withContext Pair("${p.optString("provincia")}:${p.optString("municipio")}:${p.optString("poligono")}:${p.optString("parcela")}:${p.optString("recinto")}", p.optString("uso_sigpac"))
        }
    } catch (e: Exception) { }
    Pair(null, null)
}

@Composable
fun FullScreenPhotoGallery(photos: List<String>, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }) {
        Text("Galería: ${photos.size} fotos (Toca para cerrar)", color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}
