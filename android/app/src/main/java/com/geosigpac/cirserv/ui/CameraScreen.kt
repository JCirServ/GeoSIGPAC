package com.geosigpac.cirserv.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClose: () -> Unit
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
    var isLoadingSigpac by remember { mutableStateOf(false) }
    
    // Estado para controlar la ubicación y peticiones API
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastApiLocation by remember { mutableStateOf<Location?>(null) }

    // --- EFECTO PARA LLAMADA API ---
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        
        // Evitar llamadas excesivas: Solo actualizar si nos hemos movido > 10 metros
        // o si no tenemos datos previos.
        val shouldFetch = lastApiLocation == null || loc.distanceTo(lastApiLocation!!) > 10f
        
        if (shouldFetch) {
            lastApiLocation = loc
            isLoadingSigpac = true
            try {
                val (ref, uso) = fetchRealSigpacData(loc.latitude, loc.longitude)
                // Actualizamos siempre, incluso si es null (para limpiar si salimos de parcela válida)
                sigpacRef = ref
                sigpacUso = uso
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error API SIGPAC Catch UI", e)
            } finally {
                isLoadingSigpac = false
            }
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
                Log.e("CameraScreen", "Error accediendo al GPS", e)
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

        // --- OVERLAY: INFO GEOSIGPAC ---
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

                if (isLoadingSigpac) {
                    Text(
                        text = "Consultando SIGPAC...",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (sigpacRef != null) {
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
                } else if (currentLocation != null) {
                    // Tenemos ubicación pero no datos SIGPAC (fuera de zona o error)
                    Text(
                        text = "Sin datos SIGPAC",
                        color = Color(0xFFFFAAAA),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- CONTROLES DE CÁMARA ---
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
                                onImageCaptured = onImageCaptured,
                                onError = onError
                            )
                        }
                )
                
                Box(modifier = Modifier.size(50.dp))
            }
        }
    }
}

private suspend fun fetchRealSigpacData(lat: Double, lng: Double): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        // Formato URL: [x]/[y].json -> Longitude/Latitude
        // Locale.US asegura que los decimales sean puntos (.)
        val urlString = String.format(Locale.US, "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfobypoint/4258/%.8f/%.8f.json", lng, lat)
        
        Log.e("SigpacDebug", ">>> INICIO PETICIÓN <<<")
        Log.e("SigpacDebug", "URL SOLICITADA: $urlString")
        
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000 // 10s
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "GeoSIGPAC-App/1.0")

        val responseCode = connection.responseCode
        Log.e("SigpacDebug", "HTTP STATUS: $responseCode")

        if (responseCode == 200) {
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
            Log.e("SigpacDebug", "RESPUESTA JSON RAW: $jsonResponse")

            // Parsing robusto: Puede ser Object (GeoJSON Feature) o Array
            var targetJson: JSONObject? = null

            if (jsonResponse.startsWith("[")) {
                Log.e("SigpacDebug", "Parsing: Detectado ARRAY")
                val jsonArray = JSONArray(jsonResponse)
                if (jsonArray.length() > 0) {
                    targetJson = jsonArray.getJSONObject(0)
                } else {
                    Log.e("SigpacDebug", "Parsing: Array vacío (sin parcelas encontradas)")
                }
            } else if (jsonResponse.startsWith("{")) {
                Log.e("SigpacDebug", "Parsing: Detectado OBJETO")
                targetJson = JSONObject(jsonResponse)
            } else {
                Log.e("SigpacDebug", "Parsing: Formato desconocido")
            }

            if (targetJson != null) {
                // Helper para buscar propiedad en root, 'properties' o 'features'
                fun findKey(key: String): String {
                    // 1. Directo
                    if (targetJson!!.has(key)) return targetJson!!.optString(key)
                    
                    // 2. Dentro de 'properties' (Estructura GeoJSON Feature)
                    val props = targetJson!!.optJSONObject("properties")
                    if (props != null && props.has(key)) return props.optString(key)
                    
                    // 3. Dentro de 'features' (Estructura GeoJSON FeatureCollection)
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

                Log.e("SigpacDebug", "DATOS EXTRAÍDOS -> Prov:$prov Mun:$mun Pol:$pol Parc:$parc Rec:$rec Uso:$uso")

                if (prov.isNotEmpty() && mun.isNotEmpty()) {
                    val ref = "$prov-$mun-$pol-$parc-$rec"
                    return@withContext Pair(ref, uso)
                } else {
                     Log.e("SigpacDebug", "DATOS INCOMPLETOS: Faltan provincia o municipio")
                }
            }
        } else {
            Log.e("SigpacDebug", "ERROR HTTP: El servidor devolvió código $responseCode")
            // Leer error stream si existe
            try {
                val errorStream = connection.errorStream
                if(errorStream != null) {
                     val reader = BufferedReader(InputStreamReader(errorStream))
                     val errorResponse = reader.readText()
                     Log.e("SigpacDebug", "BODY ERROR: $errorResponse")
                }
            } catch(ex: Exception) { Log.e("SigpacDebug", "No se pudo leer error stream") }
        }
    } catch (e: Exception) {
        Log.e("SigpacDebug", "EXCEPCIÓN CRÍTICA AL OBTENER DATOS", e)
        e.printStackTrace()
    }
    return@withContext Pair(null, null)
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val imageCapture = imageCapture ?: return

    val photoFile = File(
        context.cacheDir,
        "SIGPAC_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                onError(exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                Log.d("CameraScreen", "Photo capture succeeded: $savedUri")
                onImageCaptured(savedUri)
            }
        }
    )
}