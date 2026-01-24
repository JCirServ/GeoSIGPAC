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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Place
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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

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
    
    // Estados de configuración
    var aspectRatio by remember { mutableIntStateOf(AspectRatio.RATIO_4_3) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var showGrid by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showParcelSheet by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }

    // CameraX
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // GPS y Datos
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var isLoadingSigpac by remember { mutableStateOf(false) }

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

    // Cargar Preview
    LaunchedEffect(targetPreviewUri) {
        targetPreviewUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    
                    if (bitmap != null) {
                        val matrix = Matrix()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val exif = ExifInterface(input)
                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                        }
                        capturedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).asImageBitmap()
                    }
                } catch (e: Exception) { Log.e("Camera", "Preview error", e) }
            }
        }
    }

    // Inicializar Cámara
    LaunchedEffect(aspectRatio, flashMode) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        cameraProvider.unbindAll()
        val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setFlashMode(flashMode)
            .build()
        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            imageCaptureUseCase = imageCapture
        } catch (exc: Exception) { Log.e("Camera", "Bind failed", exc) }
    }

    // Ciclo de vida GPS
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        BatteryOptimizer.acquireWakeLock(context, "GeoSIGPAC:Camera")
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                locationText = "Lat: ${String.format("%.6f", l.latitude)}\nLng: ${String.format("%.6f", l.longitude)}"
                currentLocation = l
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
        }
        onDispose { locationManager.removeUpdates(listener); BatteryOptimizer.releaseWakeLock() }
    }

    // Petición SIGPAC
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            if (!isLoadingSigpac && (lastApiLocation == null || loc.distanceTo(lastApiLocation!!) > 5)) {
                isLoadingSigpac = true
                val result = fetchRealSigpacData(loc.latitude, loc.longitude)
                sigpacRef = result.first
                sigpacUso = result.second
                lastApiLocation = loc
                isLoadingSigpac = false
            }
        }
    }

    val NeonGreen = Color(0xFF00FF88)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusRingPosition = offset
                        showFocusRing = true
                        scope.launch { delay(1000); showFocusRing = false }
                        val factory = SurfaceOrientedMeteringPointFactory(previewView.width.toFloat(), previewView.height.toFloat())
                        val point = factory.createPoint(offset.x, offset.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                }
        )

        // UI de la Cámara
        if (isLandscape) {
            LandscapeUI(
                locationText, sigpacRef, sigpacUso, NeonGreen,
                onSettings = { showSettingsDialog = true },
                onProjects = onGoToProjects,
                onInfo = { showParcelSheet = true },
                onTake = { takePhoto(context, imageCaptureUseCase, projectId, sigpacRef, scope, onImageCaptured, onError) },
                matched = matchedParcelInfo != null
            )
        } else {
            PortraitUI(
                locationText, sigpacRef, sigpacUso, NeonGreen,
                capturedBitmap, (if (matchedParcelInfo != null) matchedParcelInfo.second.photos.size else photoCount),
                onSettings = { showSettingsDialog = true },
                onProjects = onGoToProjects,
                onInfo = { showParcelSheet = true },
                onTake = { takePhoto(context, imageCaptureUseCase, projectId, sigpacRef, scope, onImageCaptured, onError) },
                onMap = onGoToMap,
                onPreview = { if (matchedParcelInfo != null) showGallery = true else onClose() },
                matched = matchedParcelInfo != null
            )
        }

        // Dialogos y Hojas
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Ajustes") },
                text = { Text("Configuración de cámara...") },
                confirmButton = { Button(onClick = { showSettingsDialog = false }) { Text("Cerrar") } }
            )
        }
    }
}

// --- FUNCIONES DE SOPORTE ---

@Composable
fun PortraitUI(
    loc: String, ref: String?, uso: String?, neon: Color,
    bmp: ImageBitmap?, count: Int,
    onSettings: () -> Unit, onProjects: () -> Unit, onInfo: () -> Unit,
    onTake: () -> Unit, onMap: () -> Unit, onPreview: () -> Unit,
    matched: Boolean
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = neon) }
                IconButton(onClick = onProjects) { Icon(Icons.Default.List, null, tint = neon) }
                if (matched) IconButton(onClick = onInfo) { Icon(Icons.Default.Info, null, tint = neon) }
            }
            Box(modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Text("$loc\nRef: ${ref ?: "N/A"}", color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            // Botón Preview Corregido
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.DarkGray).clickable { onPreview() }) {
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            // Disparador
            Box(modifier = Modifier.size(80.dp).border(4.dp, neon, CircleShape).padding(4.dp).background(Color.White, CircleShape).clickable { onTake() })
            // Mapa
            IconButton(onClick = onMap, modifier = Modifier.size(64.dp).background(Color.DarkGray, CircleShape)) {
                Icon(Icons.Default.Place, null, tint = neon)
            }
        }
    }
}

@Composable
fun LandscapeUI(
    loc: String, ref: String?, uso: String?, neon: Color,
    onSettings: () -> Unit, onProjects: () -> Unit, onInfo: () -> Unit,
    onTake: () -> Unit, matched: Boolean
) {
    // Implementación simplificada para landscape
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterLeft)) {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = neon) }
            IconButton(onClick = onProjects) { Icon(Icons.Default.List, null, tint = neon) }
        }
        Box(modifier = Modifier.align(Alignment.CenterRight).size(70.dp).background(Color.White, CircleShape).clickable { onTake() })
    }
}

private fun takePhoto(
    context: Context, ic: ImageCapture?, pid: String?, ref: String?, 
    scope: CoroutineScope, onCap: (Uri) -> Unit, onErr: (ImageCaptureException) -> Unit
) {
    val imageCapture = ic ?: return
    val name = "IMG_${System.currentTimeMillis()}"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
    
    imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(o: ImageCapture.OutputFileResults) {
            o.savedUri?.let { uri ->
                scope.launch { compressPhoto(context, uri) }
                onCap(uri)
            }
        }
        override fun onError(e: ImageCaptureException) { onErr(e) }
    })
}

private suspend fun compressPhoto(context: Context, uri: Uri) {
    try {
        val file = File(context.cacheDir, "temp.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        Compressor.compress(context, file) { quality(80); resolution(1280, 720) }
    } catch (e: Exception) { e.printStackTrace() }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (h, w) = options.outHeight to options.outWidth
    var size = 1
    if (h > reqHeight || w > reqWidth) {
        val halfH = h / 2; val halfW = w / 2
        while (halfH / size >= reqHeight && halfW / size >= reqWidth) size *= 2
    }
    return size
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/$lng/$lat.json")
        val conn = url.openConnection() as HttpURLConnection
        if (conn.responseCode == 200) {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val p = json.optJSONObject("properties") ?: json.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties")
            if (p != null) {
                val ref = "${p.optString("provincia")}:${p.optString("municipio")}:${p.optString("poligono")}:${p.optString("parcela")}:${p.optString("recinto")}"
                return@withContext Pair(ref, p.optString("uso_sigpac"))
            }
        }
    } catch (e: Exception) { }
    Pair(null, null)
}
