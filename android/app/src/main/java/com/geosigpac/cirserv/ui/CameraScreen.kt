package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen(
    projectId: String?,
    lastCapturedUri: Uri?,
    photoCount: Int,
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

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    
    // CRÍTICO: Usamos COMPATIBLE (TextureView) para evitar fallos de memoria gráfica en transiciones
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var currentLinearZoom by remember { mutableFloatStateOf(0f) }
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }

    val MapIcon = remember {
        ImageVector.Builder(name = "Map", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20.5f, 3.0f); lineTo(20.34f, 3.03f); lineTo(15.0f, 5.1f); lineTo(9.0f, 3.0f); lineTo(3.36f, 4.9f); curveTo(3.15f, 4.97f, 3.0f, 5.15f, 3.0f, 5.38f); verticalLineTo(20.5f); curveTo(3.0f, 20.78f, 3.22f, 21.0f, 3.5f, 21.0f); lineTo(3.66f, 20.97f); lineTo(9.0f, 18.9f); lineTo(15.0f, 21.0f); lineTo(20.64f, 19.1f); curveTo(20.85f, 19.03f, 21.0f, 18.85f, 21.0f, 18.62f); verticalLineTo(3.5f); curveTo(21.0f, 3.22f, 20.78f, 3.0f, 20.5f, 3.0f); close(); moveTo(15.0f, 19.0f); lineTo(9.0f, 16.89f); verticalLineTo(5.0f); lineTo(15.0f, 7.11f); verticalLineTo(19.0f); close()
            }
        }.build()
    }

    LaunchedEffect(lastCapturedUri) {
        lastCapturedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    capturedBitmap = bitmap?.asImageBitmap()
                } catch (e: Exception) { Log.e("CameraScreen", "Error bitmap", e) }
            }
        }
    }

    DisposableEffect(aspectRatio, flashMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
                
                val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(aspectRatio)
                    .setFlashMode(flashMode)
                    .build()

                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                imageCaptureUseCase = imageCapture
                
                camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
                    currentLinearZoom = state.linearZoom
                }
            } catch (exc: Exception) { Log.e("CameraScreen", "Binding failed", exc) }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // CRÍTICO: Liberar la cámara inmediatamente para evitar NPE en hilos de background nativos
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val loc = currentLocation
            val now = System.currentTimeMillis()
            if (loc != null) {
                val distance = if (lastApiLocation != null) loc.distanceTo(lastApiLocation!!) else Float.MAX_VALUE
                if ((distance > 5.0f || (now - lastApiTimestamp) > 6000) && !isLoadingSigpac) {
                    isLoadingSigpac = true
                    lastApiLocation = loc
                    lastApiTimestamp = now
                    try {
                        val (ref, uso) = fetchRealSigpacData(loc.latitude, loc.longitude)
                        if (ref != null) { sigpacRef = ref; sigpacUso = uso; showNoDataMessage = false } 
                        else { showNoDataMessage = true }
                    } catch (e: Exception) { } finally { isLoadingSigpac = false }
                }
            }
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}"
                currentLocation = location
            }
            override fun onProviderDisabled(p: String) { locationText = "GPS Desactivado" }
            override fun onProviderEnabled(p: String) {}
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
        }
        onDispose { locationManager.removeUpdates(listener) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); scaleType = PreviewView.ScaleType.FILL_CENTER } }
        )

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawLine(Color.White.copy(0.3f), Offset(w/3, 0f), Offset(w/3, h), 2f)
                drawLine(Color.White.copy(0.3f), Offset(2*w/3, 0f), Offset(2*w/3, h), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, h/3), Offset(w, h/3), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, 2*h/3), Offset(w, 2*h/3), 2f)
            }
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                showFocusRing = true; focusRingPosition = offset
                scope.launch { delay(1000); showFocusRing = false }
                camera?.let { cam ->
                    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                }
            }
        })

        if (showFocusRing && focusRingPosition != null) {
            Box(modifier = Modifier.offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.x.toDp() - 25.dp }, y = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.y.toDp() - 25.dp }).size(50.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        }

        if (isLandscape) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = onGoToProjects, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.List, null, tint = Color.White) }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Settings, null, tint = Color.White) }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { InfoBox(locationText, sigpacRef, sigpacUso, showNoDataMessage) }
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) { ShutterButton { takePhoto(context, imageCaptureUseCase, projectId, sigpacRef, onImageCaptured, onError) } }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    PreviewThumbnail(capturedBitmap, photoCount, onClose)
                    IconButton(onClick = onGoToMap, modifier = Modifier.size(60.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp))) { Icon(MapIcon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)) { ZoomSliderHorizontal(currentLinearZoom) { camera?.cameraControl?.setLinearZoom(it) } }
        } else {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = onGoToProjects, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.List, null, tint = Color.White) }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Settings, null, tint = Color.White) }
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)) { InfoBox(locationText, sigpacRef, sigpacUso, showNoDataMessage) }
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 48.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                ZoomSliderHorizontal(currentLinearZoom) { camera?.cameraControl?.setLinearZoom(it) }
                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PreviewThumbnail(capturedBitmap, photoCount, onClose)
                    ShutterButton { takePhoto(context, imageCaptureUseCase, projectId, sigpacRef, onImageCaptured, onError) }
                    IconButton(onClick = onGoToMap, modifier = Modifier.size(60.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(12.dp))) { Icon(MapIcon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("Cámara") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formato", style = MaterialTheme.typography.labelMedium)
                    Row {
                        RadioButton(aspectRatio == AspectRatio.RATIO_4_3, { aspectRatio = AspectRatio.RATIO_4_3 }); Text("4:3")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(aspectRatio == AspectRatio.RATIO_16_9, { aspectRatio = AspectRatio.RATIO_16_9 }); Text("16:9")
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showGrid, { showGrid = it }); Text("Cuadrícula")
                    }
                }
            }, confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("OK") } })
        }
    }
}

@Composable
fun InfoBox(loc: String, ref: String?, uso: String?, empty: Boolean) {
    Box(modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text(loc, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            if (ref != null) {
                Text(ref, color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (uso != null) Text(uso, color = Color.White, fontSize = 10.sp)
            } else if (empty) {
                Text("Sin datos SIGPAC", color = Color.LightGray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit) {
    Box(modifier = Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(4.dp).background(Color.White, CircleShape).clickable { onClick() })
}

@Composable
fun PreviewThumbnail(bmp: ImageBitmap?, count: Int, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray).border(1.dp, Color.White.copy(0.5f), RoundedCornerShape(12.dp)).clickable { onClick() }) {
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (count > 0) Box(modifier = Modifier.offset(x = 6.dp, y = (-6).dp).size(20.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
            Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ZoomSliderHorizontal(value: Float, onValueChange: (Float) -> Unit) {
    Slider(value = value, onValueChange = onValueChange, modifier = Modifier.width(200.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Yellow))
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val url = URL(String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = if (response.trim().startsWith("[")) JSONArray(response).optJSONObject(0) else JSONObject(response)
            if (json != null) {
                val prov = json.optString("provincia"); val mun = json.optString("municipio")
                val pol = json.optString("poligono"); val parc = json.optString("parcela")
                if (prov.isNotEmpty()) return@withContext Pair("$prov:$mun:$pol:$parc", json.optString("uso_sigpac"))
            }
        }
    } catch (e: Exception) { }
    return@withContext Pair(null, null)
}

private fun takePhoto(context: Context, imageCapture: ImageCapture?, projectId: String?, sigpacRef: String?, onCaptured: (Uri) -> Unit, onError: (ImageCaptureException) -> Unit) {
    val ic = imageCapture ?: return
    val name = "IMG_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/GeoSIGPAC")
    }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()
    ic.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(e: ImageCaptureException) { onError(e) }
        override fun onImageSaved(res: ImageCapture.OutputFileResults) { res.savedUri?.let { onCaptured(it) } }
    })
}