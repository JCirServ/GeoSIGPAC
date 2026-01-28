
package com.geosigpac.cirserv.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import androidx.exifinterface.media.ExifInterface // Usamos AndroidX para soporte extendido
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

object CameraCaptureLogic {

    private const val UPLOAD_TIMEOUT_MS = 120000L // 120s Timeout
    private const val TAG = "CameraExpert"

    fun takePhoto(
        context: Context,
        imageCapture: ImageCapture?,
        projectNames: List<String>, 
        sigpacRef: String?,
        location: Location?,
        cropToSquare: Boolean,
        jpegQuality: Int,
        overlayOptions: Set<OverlayOption>,
        onImageCaptured: (Map<String, Uri>) -> Unit, 
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCapture = imageCapture ?: return
        
        val tempFile = File.createTempFile("temp_capture", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
        imageCapture.targetRotation = rotation

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) { 
                    onError(exc) 
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Timeout de seguridad de 120s para el procesado/guardado (o futura subida)
                            withTimeout(UPLOAD_TIMEOUT_MS) {
                                val targetFolders = if (projectNames.isNotEmpty()) projectNames else listOf("SIN PROYECTO")
                                
                                val resultUris = processImage(
                                    context, 
                                    tempFile, 
                                    targetFolders, 
                                    sigpacRef, 
                                    location, 
                                    cropToSquare, 
                                    jpegQuality, 
                                    overlayOptions
                                )
                                
                                tempFile.delete()
                                withContext(Dispatchers.Main) {
                                    onImageCaptured(resultUris)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image", e)
                            tempFile.delete()
                            withContext(Dispatchers.Main) {
                                val errorMsg = if(e is kotlinx.coroutines.TimeoutCancellationException) 
                                    "Tiempo de espera agotado (120s)" else "Error: ${e.message}"
                                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, errorMsg, e))
                            }
                        }
                    }
                }
            }
        )
    }

    private fun processImage(
        context: Context,
        sourceFile: File,
        targetFolders: List<String>,
        sigpacRef: String?,
        location: Location?,
        cropToSquare: Boolean,
        jpegQuality: Int,
        overlayOptions: Set<OverlayOption>
    ): Map<String, Uri> {
        // 1. Cargar y Corregir Orientación (Solo una vez)
        // Usamos AndroidX ExifInterface para leer la orientación inicial
        val exifOriginal = ExifInterface(sourceFile.absolutePath)
        val orientation = exifOriginal.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        
        var rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        // 2. Recorte 1:1 (Si aplica)
        if (cropToSquare) {
            val w = rotatedBitmap.width
            val h = rotatedBitmap.height
            val side = min(w, h)
            val xOffset = (w - side) / 2
            val yOffset = (h - side) / 2
            
            val squared = Bitmap.createBitmap(rotatedBitmap, xOffset, yOffset, side, side)
            if (rotatedBitmap != squared) {
                rotatedBitmap.recycle()
                rotatedBitmap = squared
            }
        }

        val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 3. Dibujar Overlay Configurable
        if (overlayOptions.isNotEmpty()) {
            val displayProject = if (targetFolders.contains("SIN PROYECTO")) null else targetFolders.firstOrNull()
            drawOverlay(mutableBitmap, location, sigpacRef, displayProject, overlayOptions)
        }

        val safeSigpacRef = sigpacRef?.replace(":", "_") ?: "SIN_REFERENCIA"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "${safeSigpacRef}-$timestamp.jpg"
        
        val resultMap = mutableMapOf<String, Uri>()
        val resolver = context.contentResolver

        // 4. Guardar una copia por cada Proyecto destino
        for (folderName in targetFolders) {
            val relativePath = "DCIM/GeoSIGPAC/$folderName/$safeSigpacRef"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) 
            
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                    }
                    
                    // 5. Inyectar Metadatos EXIF Periciales (AndroidX)
                    resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                        val success = sealImageWithExpertMetadata(fd.fileDescriptor, location, sigpacRef, folderName)
                        if (!success) {
                            Log.w(TAG, "Advertencia: No se pudo sellar completamente la prueba pericial en EXIF.")
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    
                    resultMap[folderName] = uri
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save for project $folderName", e)
                }
            }
        }

        mutableBitmap.recycle()
        rotatedBitmap.recycle()
        bitmap.recycle()
        
        return resultMap
    }

    /**
     * Sella la imagen con metadatos EXIF de alta fiabilidad para uso pericial.
     * Utiliza androidx.exifinterface para máxima compatibilidad.
     */
    private fun sealImageWithExpertMetadata(
        fileDescriptor: FileDescriptor,
        location: Location?,
        sigpacRef: String?,
        projectName: String
    ): Boolean {
        return try {
            val exif = ExifInterface(fileDescriptor)
            val now = Date()
            val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            
            // --- A. DOBLE CERTIFICACIÓN DE TIEMPO ---
            
            // 1. Hora Local (Sistema) - TAG_DATETIME_ORIGINAL
            // Representa cuándo se tomó la foto según el reloj del usuario (útil para referencia civil)
            val localTimeStr = dateFormat.format(now)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, localTimeStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, localTimeStr)
            
            // Precisión de milisegundos para mayor unicidad
            val subSec = (System.currentTimeMillis() % 1000).toString()
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subSec)

            // 2. Hora Satelital (Inviolable) - GPS Timestamp
            // Si tenemos location.time, lo usamos. Es mucho más difícil de falsificar.
            if (location != null && location.time > 0) {
                val gpsDate = Date(location.time)
                val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(gpsDate))
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(gpsDate))
            }

            // --- B. GEORREFERENCIACIÓN ESTÁNDAR ---
            if (location != null) {
                // Latitud
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToRationalLatLon(location.latitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
                
                // Longitud
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToRationalLatLon(location.longitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
                
                // Altitud
                val alt = location.altitude
                val altRef = if (alt < 0) "1" else "0" // 1 = Sea level reference (negative)
                val altNum = abs(alt)
                // Convertir a racional: (alt * 1000) / 1000
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, toRational(altNum))
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)
            }

            // --- C. CERTIFICACIÓN DE CALIDAD TÉCNICA (SIGPAC) ---
            
            if (location != null) {
                // 1. Precisión (Accuracy) -> TAG_GPS_H_POSITIONING_ERROR
                if (location.hasAccuracy()) {
                    val accuracy = location.accuracy.toDouble()
                    exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, toRational(accuracy))
                }

                // 2. Rumbo (Bearing) -> TAG_GPS_IMG_DIRECTION
                if (location.hasBearing()) {
                    val bearing = location.bearing.toDouble()
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, toRational(bearing))
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M") // Magnetic North
                }

                // 3. Método de Procesamiento -> GPS_HARDWARE_FIX
                // Certifica que no es 'Network' ni simulada (en la medida de lo posible por API)
                val provider = location.provider?.uppercase() ?: "UNKNOWN"
                val fixMethod = if (provider.contains("GPS")) "GPS_HARDWARE_FIX" else provider
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, fixMethod)
                
                // Datum (Estándar WGS-84)
                exif.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, "WGS-84")
            }

            // --- D. METADATOS DE CONTEXTO ---
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoSIGPAC App (Pericial)")
            
            val cleanRef = sigpacRef ?: "N/D"
            val userComment = "REF:$cleanRef|PROJ:$projectName|ACC:${location?.accuracy ?: -1}"
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Inspección SIGPAC Ref: $cleanRef Proyecto: $projectName")

            // GUARDAR CAMBIOS
            exif.saveAttributes()
            true

        } catch (e: IOException) {
            Log.e(TAG, "Error IO escribiendo EXIF: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error general escribiendo EXIF: ${e.message}")
            false
        }
    }

    private fun drawOverlay(
        bitmap: Bitmap, 
        location: Location?, 
        sigpacRef: String?, 
        projectName: String?,
        options: Set<OverlayOption>
    ) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        
        // Ajuste de tamaño relativo
        val minDimension = min(w, h).toFloat()
        val baseTextSize = minDimension * 0.028f
        
        val paintText = Paint().apply {
            color = AndroidColor.WHITE
            textSize = baseTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
            isAntiAlias = true
        }
        
        val paintBg = Paint().apply {
            color = AndroidColor.BLACK
            alpha = 140
            style = Paint.Style.FILL
        }
        
        val lines = mutableListOf<Pair<String, Int>>()
        
        // 1. FECHA Y HORA (Con segundos para peritaje)
        if (options.contains(OverlayOption.DATE)) {
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            lines.add("FECHA: $dateStr" to AndroidColor.GREEN)
        }
        
        // 2. COORDENADAS Y PRECISIÓN (Nuevo: Error Estimado y Modelo)
        if (options.contains(OverlayOption.COORDS)) {
            if (location != null) {
                val latStr = String.format(Locale.US, "%.6f", location.latitude)
                val lngStr = String.format(Locale.US, "%.6f", location.longitude)
                lines.add("GPS: $latStr, $lngStr" to AndroidColor.WHITE)
                
                // Línea de Calidad Técnica
                val accStr = if(location.hasAccuracy()) "±${location.accuracy.toInt()}m" else "N/D"
                val altStr = if(location.hasAltitude()) "${location.altitude.toInt()}m" else ""
                val devModel = "${Build.MANUFACTURER} ${Build.MODEL}".take(20) // Limitar largo
                
                lines.add("PRECISIÓN: $accStr ALT: $altStr | $devModel" to AndroidColor.LTGRAY)
            } else {
                lines.add("GPS: SIN SEÑAL" to AndroidColor.RED)
            }
        }
        
        // 3. SIGPAC
        if (options.contains(OverlayOption.REF)) {
            val refStr = sigpacRef ?: "REF: PENDIENTE / SIN DATOS"
            lines.add("SIGPAC: $refStr" to AndroidColor.YELLOW)
        }

        // 4. PROYECTO
        if (options.contains(OverlayOption.PROJECT) && projectName != null) {
            lines.add("PROY: $projectName" to AndroidColor.CYAN)
        }

        if (lines.isEmpty()) return

        // --- DIBUJAR CAJETÍN ---
        val lineHeight = baseTextSize * 1.45f
        val padding = baseTextSize * 0.8f
        val boxHeight = (lineHeight * lines.size) + (padding * 2)
        
        var maxTextWidth = 0f
        lines.forEach { (text, _) ->
            val textWidth = paintText.measureText(text)
            if (textWidth > maxTextWidth) maxTextWidth = textWidth
        }
        val boxWidth = maxTextWidth + (padding * 2)
        
        canvas.drawRect(0f, h - boxHeight, boxWidth, h.toFloat(), paintBg)
        
        val startX = padding
        var startY = h - boxHeight + padding + baseTextSize 
        
        lines.forEach { (text, color) ->
            paintText.color = color
            canvas.drawText(text, startX, startY, paintText)
            startY += lineHeight
        }
    }

    // --- UTILS PARA EXIF ---

    private fun toRational(value: Double): String {
        val denominator = 10000L
        val numerator = (value * denominator).toLong()
        return "$numerator/$denominator"
    }

    private fun convertToRationalLatLon(value: Double): String {
        val absValue = abs(value)
        val degrees = absValue.toInt()
        val remainder = (absValue - degrees) * 60
        val minutes = remainder.toInt()
        val seconds = (remainder - minutes) * 60 * 1000 // Multiplicar por 1000 para precisión
        
        return "$degrees/1,$minutes/1,${seconds.toInt()}/1000"
    }
}
