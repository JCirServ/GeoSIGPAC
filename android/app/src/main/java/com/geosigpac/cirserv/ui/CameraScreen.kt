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
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.absoluteValue

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
    var locationText by remember { mutableStateOf("Sin cobertura GPS") }
    var sigpacRef by remember { mutableStateOf<String?>(null) }
    var sigpacUso by remember { mutableStateOf<String?>(null) }

    // --- OBTENCIÓN DE UBICACIÓN ---
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Listener para actualizaciones
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // 1. Actualizar coordenadas
                locationText = "Lat: ${String.format("%.6f", location.latitude)}\nLng: ${String.format("%.6f", location.longitude)}"
                
                // 2. Calcular datos SIGPAC (Simulación basada en coordenadas)
                // Nota: En producción, esto haría una llamada a API WFS o decodificaría MVT.
                val data = calculateMockSigpacData(location.latitude, location.longitude)
                sigpacRef = data.first
                sigpacUso = data.second
            }
            
            override fun onProviderEnabled(provider: String) {}
            
            override fun onProviderDisabled(provider: String) {
                locationText = "Sin cobertura GPS"
                sigpacRef = null
                sigpacUso = null
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // Permisos y solicitud de updates
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // 1. Intentar obtener última ubicación conocida
                val lastKnownGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastKnownNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val lastKnown = lastKnownGPS ?: lastKnownNet
                
                if (lastKnown != null) {
                    locationText = "Lat: ${String.format("%.6f", lastKnown.latitude)}\nLng: ${String.format("%.6f", lastKnown.longitude)}"
                    val data = calculateMockSigpacData(lastKnown.latitude, lastKnown.longitude)
                    sigpacRef = data.first
                    sigpacUso = data.second
                }

                // 2. Solicitar actualizaciones en tiempo real
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, listener)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error accediendo al GPS", e)
                locationText = "Sin cobertura GPS"
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
                .padding(top = 32.dp, end = 16.dp)
                .background(Color.Gray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                // Coordenadas
                Text(
                    text = locationText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )

                // Referencia SIGPAC
                if (sigpacRef != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ref: $sigpacRef",
                        color = Color(0xFFFFFF00), // Amarillo para destacar
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Uso: $sigpacUso",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal
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
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                   Text("X", color = Color.White, fontWeight = FontWeight.Bold)
                }

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

/**
 * Función auxiliar que genera datos SIGPAC "falsos" pero consistentes basados en lat/lng.
 * En una app real, esto consultaría un servicio WFS o decodificaría la geometría MVT.
 */
private fun calculateMockSigpacData(lat: Double, lng: Double): Pair<String, String> {
    // Provincia: 46 (Valencia por defecto)
    val prov = "46"
    
    // Generar códigos semi-aleatorios basados en coordenadas para que cambien al moverse
    val latPart = (lat * 10000).toInt().absoluteValue
    val lngPart = (lng * 10000).toInt().absoluteValue
    
    val mun = (latPart % 300 + 1).toString()
    val pol = ((lngPart / 100) % 50 + 1).toString()
    val parc = (latPart % 200 + 1).toString()
    val rec = (lngPart % 5 + 1).toString()
    
    // Usos posibles
    val usos = listOf("CF", "OV", "TA", "FS", "PR", "PA", "ZU")
    val uso = usos[(latPart + lngPart) % usos.size]

    // Formato: provincia-municipio-poligono-parcela-recinto
    return "$prov-$mun-$pol-$parc-$rec" to uso
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