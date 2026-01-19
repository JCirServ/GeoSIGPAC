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
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.graphics.TransformOrigin

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
    
    // --- ESTADOS DE CONFIGURACIÓN DE CÁMARA ---
    var aspectRatio by remember { mutableIntStateOf(AspectRatio.RATIO_4_3) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var showGrid by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // --- OBJETOS CAMERAX ---
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Vista previa persistente
    val previewView = remember { PreviewView(context) }

    // --- ESTADO ZOOM ---
    var currentLinearZoom by remember { mutableFloatStateOf(0f) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }

    // --- ESTADO TAP TO FOCUS ---
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // --- ESTADO PREVISUALIZACIÓN FOTO (Carga de Bitmap) ---
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Efecto para cargar el bitmap
    LaunchedEffect(lastCapturedUri) {
        lastCapturedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    capturedBitmap = bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Estado para información GPS y SIGPAC
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiTimestamp by remember { mutableStateOf(0L) }
    
    // --- ICONO MAPA MANUAL ---
    val MapIcon = remember {
        ImageVector.Builder(
            name = "Map",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20.5f, 3.0f)
                lineTo(20.34f, 3.03f)
                lineTo(15.0f, 5.1f)
                lineTo(9.0f, 3.0f)
                lineTo(3.36f, 4.9f)
                curveTo(3.15f, 4.97f, 3.0f, 5.15f, 3.0f, 5.38f)
                verticalLineTo(20.5f)
                curveTo(3.0f, 20.78f, 3.22f, 21.0f, 3.5f, 21.0f)
                lineTo(3.66f, 20.97f)
                lineTo(9.0f, 18.9f)
                lineTo(15.0f, 21.0f)
                lineTo(20.64f, 19.1f)
                curveTo(20.85f, 19.03f, 21.0f, 18.85f, 21.0f, 18.62f)
                verticalLineTo(3.5f)
                curveTo(21.0f, 3.22f, 20.78f, 3.0f, 20.5f, 3.0f)
                close()
                moveTo(15.0f, 19.0f)
                lineTo(9.0f, 16.89f)
                verticalLineTo(5.0f)
                lineTo(15.0f, 7.11f)
                verticalLineTo(19.0f)
                close()
            }
        }.build()
    }

    // --- OBSERVADOR DE ESTADO DE ZOOM ---
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        cam.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
            currentLinearZoom = state.linearZoom
            currentZoomRatio = state.zoomRatio
        }
    }

    // --- VINCULACIÓN DE CÁMARA ---
    LaunchedEffect(aspectRatio, flashMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(aspectRatio)
                .setFlashMode(flashMode)
                .build()
            try {
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                imageCaptureUseCase = imageCapture
            } catch (exc: Exception) {
                Log.e("CameraScreen", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- BUCLE SIGPAC Y GPS (Lógica igual que antes) ---
    LaunchedEffect(Unit) {
        while (true) {
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
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
            } catch (e: Exception) { locationText = "Error GPS" }
        }
        onDispose {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(listener)
            }
        }
    }

    // --- COMPONENTES UI REUTILIZABLES ---
    
    // Botones Superiores (Config y Proyectos)
    val TopLeftButtons = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable { onGoToProjects() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.List, "Proyectos", tint = Color.White) }
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable { showSettingsDialog = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Settings, "Configuración", tint = Color.White) }
        }
    }

    // Cajetín de Información
    val InfoBox = @Composable {
        Box(
            modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(locationText, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Spacer(Modifier.height(6.dp))
                if (sigpacRef != null) {
                    Text("Ref: $sigpacRef", color = Color(0xFFFFFF00), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
                    Text("Uso: ${sigpacUso ?: "N/D"}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else if (showNoDataMessage) {
                    Text("Sin datos SIGPAC", color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                } else {
                    Text("Analizando zona...", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    // Botón Disparador
    val ShutterButton = @Composable {
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .background(Color.White, CircleShape)
                .clickable {
                    takePhoto(context, imageCaptureUseCase, projectId, sigpacRef, 
                        onImageCaptured = { uri -> onImageCaptured(uri) }, onError)
                }
        )
    }

    // Preview de Foto con Badge
    val PreviewButton = @Composable {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp)) 
                    .background(Color.Black.copy(0.5f))
                    .border(2.dp, Color.White.copy(0.7f), RoundedCornerShape(24.dp))
                    .clickable { onClose() }, 
                contentAlignment = Alignment.Center
            ) { 
                if (capturedBitmap != null) {
                    Image(bitmap = capturedBitmap!!, contentDescription = "Preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(imageVector = Icons.Default.Image, contentDescription = "Sin Foto", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
            if (photoCount > 0) {
                Box(
                    modifier = Modifier.offset(x = 8.dp, y = (-8).dp).size(28.dp).background(Color.Red, CircleShape).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = photoCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Botón Mapa
    val MapButton = @Composable {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(0.5f))
                .border(2.dp, Color.White.copy(0.7f), RoundedCornerShape(24.dp))
                .clickable { onGoToMap() },
            contentAlignment = Alignment.Center
        ) { Icon(MapIcon, "Mapa", tint = Color.White, modifier = Modifier.size(36.dp)) }
    }

    // Slider Zoom (Vertical u Horizontal)
    val ZoomControl = @Composable { isLandscapeMode: Boolean ->
        val containerModifier = if (isLandscapeMode) {
             // Horizontal y alargado
             Modifier
                .width(260.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
        } else {
            // Vertical (contenedor alto)
            Modifier
                .height(300.dp)
                .width(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(15.dp))
        }

        Box(
            modifier = containerModifier,
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = currentLinearZoom,
                onValueChange = { valz -> camera?.cameraControl?.setLinearZoom(valz) },
                modifier = if (isLandscapeMode) {
                    Modifier.width(240.dp) // Slider horizontal normal
                } else {
                    Modifier
                        .graphicsLayer {
                            rotationZ = 270f
                            transformOrigin = TransformOrigin.Center
                        }
                        .requiredWidth(260.dp) // Ancho rotado se convierte en alto visual
                }, 
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFFFD700),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }

    // --- UI LAYOUT PRINCIPAL ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
        
        // 1. Vista de Cámara (Fondo)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); scaleType = PreviewView.ScaleType.FILL_CENTER } }
        )
        
        // 2. Anillo de Enfoque
        if (showFocusRing && focusRingPosition != null) {
            Box(modifier = Modifier.offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.x.toDp() - 25.dp }, y = with(androidx.compose.ui.platform.LocalDensity.current) { focusRingPosition!!.y.toDp() - 25.dp }).size(50.dp).border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape))
        }

        // 3. Grid
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawLine(Color.White.copy(0.3f), Offset(w/3, 0f), Offset(w/3, h), 2f)
                drawLine(Color.White.copy(0.3f), Offset(2*w/3, 0f), Offset(2*w/3, h), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, h/3), Offset(w, h/3), 2f)
                drawLine(Color.White.copy(0.3f), Offset(0f, 2*h/3), Offset(w, 2*h/3), 2f)
            }
        }

        // --- CAPA DE INTERFAZ (Layout Responsivo) ---
        
        if (isLandscape) {
            // ================= LANDSCAPE LAYOUT =================
            
            // Arriba Izquierda: Botones
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                TopLeftButtons()
            }

            // Arriba Derecha: Info
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                InfoBox()
            }

            // Centro Derecha: Disparador
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                ShutterButton()
            }

            // Abajo Izquierda: Preview y Mapa (Horizontal)
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                PreviewButton()
                MapButton()
            }

            // Abajo Derecha: Slider Horizontal
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 32.dp)) {
                // Para que no se solape con el disparador si la pantalla es estrecha, 
                // lo movemos un poco a la izquierda del borde derecho
                ZoomControl(true)
            }

        } else {
            // ================= PORTRAIT LAYOUT (Original) =================
            
            // Arriba Izquierda
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)) {
                TopLeftButtons()
            }

            // Arriba Derecha
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) {
                InfoBox()
            }

            // Centro Izquierda: Zoom Vertical
            Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
                ZoomControl(false)
            }

            // Abajo: Fila de controles (Preview - Disparador - Mapa)
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewButton()
                    ShutterButton()
                    MapButton()
                }
            }
        }
        
        // --- DIÁLOGO CONFIGURACIÓN ---
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configuración de Cámara") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Resolución (Aspect Ratio)", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = aspectRatio == AspectRatio.RATIO_4_3, onClick = { aspectRatio = AspectRatio.RATIO_4_3 }); Text("4:3")
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = aspectRatio == AspectRatio.RATIO_16_9, onClick = { aspectRatio = AspectRatio.RATIO_16_9 }); Text("16:9")
                        }
                        Divider()
                        Text("Flash", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_AUTO, onClick = { flashMode = ImageCapture.FLASH_MODE_AUTO }); Text("Auto")
                            RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_ON, onClick = { flashMode = ImageCapture.FLASH_MODE_ON }); Text("On")
                            RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_OFF, onClick = { flashMode = ImageCapture.FLASH_MODE_OFF }); Text("Off")
                        }
                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showGrid = !showGrid }) {
                            Checkbox(checked = showGrid, onCheckedChange = { showGrid = it }); Text("Mostrar Cuadrícula")
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Cerrar") } }
            )
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
    projectId: String?,
    sigpacRef: String?,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val imageCapture = imageCapture ?: return
    val projectFolder = projectId ?: "SIN PROYECTO"
    val safeSigpacRef = sigpacRef?.replace(":", "_") ?: "SIN_REFERENCIA"
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val filename = "${safeSigpacRef}-$timestamp.jpg"
    val relativePath = "DCIM/GeoSIGPAC/$projectFolder/$safeSigpacRef"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) { onError(exc) }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.EMPTY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != Uri.EMPTY) {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    try { context.contentResolver.update(savedUri, values, null, null) } catch (e: Exception) { e.printStackTrace() }
                }
                onImageCaptured(savedUri)
            }
        }
    )
}