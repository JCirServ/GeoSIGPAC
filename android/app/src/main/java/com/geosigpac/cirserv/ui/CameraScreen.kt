package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

@Composable
fun CameraScreen(
    projectId: String?,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClose: () -> Unit,
    onGoToMap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Estado para mostrar información
    var locationText by remember { mutableStateOf("Obteniendo ubicación...") }
    
    // Estado para datos SIGPAC
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }
    
    // Control de UI para errores (Debounce)
    var showNoDataMessage by remember { mutableStateOf(false) }
    var isLoadingSigpac by remember { mutableStateOf(false) } 
    
    // Estado para controlar la ubicación
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    
    // Estado para lógica de refresco (Distancia y Tiempo)
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

    // --- BUCLE HÍBRIDO: DISTANCIA (>3m) O TIEMPO (>5s) ---
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
                        
                        if (ref != null) {
                            sigpacRef = ref
                            sigpacUso = uso
                            showNoDataMessage = false
                        } else {
                            sigpacRef = null
                            sigpacUso = null
                            delay(2000)
                            if (sigpacRef == null) {
                                showNoDataMessage = true
                            }
                        }
                    } catch (e: Exception) {
                        // Log silencioso
                    } finally {
                        isLoadingSigpac = false
                    }
                }
            }
            delay(500)
        }
    }

    // --- OBTENCIÓN DE UBICACIÓN ---
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}"
                currentLocation = location
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                locationText = "Sin señal GPS"
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val lastKnownGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastKnownNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val lastKnown = lastKnownGPS ?: lastKnownNet
                
                if (lastKnown != null) {
                    locationText = "Lat: ${String.format("%.6f", lastKnown.latitude)}\nLng: ${String.format("%.6f", lastKnown.longitude)}"
                    currentLocation = lastKnown
                }

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
            } catch (e: Exception) {
                locationText = "Error GPS"
            }
        } else {
            locationText = "Sin permisos GPS"
        }

        onDispose {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(listener)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()

                    val preview = Preview.Builder().build()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        previewUseCase = preview
                        imageCaptureUseCase = imageCapture
                    } catch (exc: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
        
        // --- BOTÓN VOLVER (TOP-LEFT) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 16.dp)
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { 
                    onClose() 
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Volver",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // --- OVERLAY: INFO GEOSIGPAC (TOP-RIGHT) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = locationText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (sigpacRef != null) {
                    Text(
                        text = "Ref: $sigpacRef",
                        color = Color(0xFFFFFF00),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Text(
                        text = "Uso: ${sigpacUso ?: "N/D"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                } else if (showNoDataMessage) {
                    Text(
                        text = "Sin datos SIGPAC",
                        color = Color(0xFFFFAAAA),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "Analizando zona...",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- CONTROLES DE CÁMARA (BOTTOM) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancelar
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                   Text("X", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Disparador
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(Color.White, CircleShape)
                        .clickable {
                            takePhoto(
                                context = context,
                                imageCapture = imageCaptureUseCase,
                                projectId = projectId,
                                sigpacRef = sigpacRef,
                                onImageCaptured = onImageCaptured,
                                onError = onError
                            )
                        }
                )
                
                // Botón Mapa
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .clickable { onGoToMap() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MapIcon,
                        contentDescription = "Ir al Mapa",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000 
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")

        if (connection.responseCode == 200) {
            val stream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(stream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            connection.disconnect()
            
            val jsonResponse = response.toString().trim()
            var targetJson: JSONObject? = null

            if (jsonResponse.startsWith("[")) {
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) {
                    targetJson = jsonArray.getJSONObject(0)
                }
            } else if (jsonResponse.startsWith("{")) {
                targetJson = JSONObject(jsonResponse)
            }

            if (targetJson != null) {
                fun findKey(key: String): String {
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    val props = targetJson!!.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optString(key)
                    val features = targetJson!!.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        val firstFeature = features.getJSONObject(0)
                        val featProps = firstFeature.optJSONObject("properties")
                        if (featProps != null && featProps.has(key)) return featProps.optString(key)
                    }
                    return ""
                }

                val prov = findKey("provincia")
                val mun = findKey("municipio")
                val pol = findKey("poligono")
                val parc = findKey("parcela")
                val rec = findKey("recinto")
                val uso = findKey("uso_sigpac")

                if (prov.isNotEmpty() && mun.isNotEmpty()) {
                    val ref = "$prov:$mun:$pol:$parc:$rec"
                    return@withContext Pair(ref, uso)
                }
            }
        }
    } catch (e: Exception) {
        // Ignored
    }
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

    // 1. Determinar nombres de carpetas y archivo
    val projectFolder = projectId ?: "SIN PROYECTO"
    
    // Sanitizar referencia SIGPAC (reemplazar ':' por '_' para sistema de archivos)
    val safeSigpacRef = sigpacRef?.replace(":", "_") ?: "SIN_REFERENCIA"
    
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val filename = "${safeSigpacRef}-$timestamp.jpg"
    
    // Estructura de ruta relativa dentro de DCIM
    val relativePath = "DCIM/GeoSIGPAC/$projectFolder/$safeSigpacRef"

    // 2. Configurar metadatos para MediaStore
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // Marcar como pendiente mientras se escribe
        }
    }

    // 3. Crear opciones de salida
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    // 4. Capturar
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                onError(exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.EMPTY
                
                // Si estamos en Android Q+, marcar como ya no pendiente
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != Uri.EMPTY) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    try {
                        context.contentResolver.update(savedUri, values, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                onImageCaptured(savedUri)
            }
        }
    )
}