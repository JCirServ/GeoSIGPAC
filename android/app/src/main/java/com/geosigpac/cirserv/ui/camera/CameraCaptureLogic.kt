
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
import androidx.exifinterface.media.ExifInterface
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

object CameraCaptureLogic {

    private const val UPLOAD_TIMEOUT_MS = 120000L 

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
        val captureUseCase = imageCapture ?: return
        
        val tempFile = File.createTempFile("temp_capture", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
        captureUseCase.targetRotation = rotation

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) { 
                    onError(exc) 
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
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
                            Log.e("Camera", "Error processing image", e)
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
        
        if (cropToSquare) {
            val side = min(rotatedBitmap.width, rotatedBitmap.height)
            val squared = Bitmap.createBitmap(rotatedBitmap, (rotatedBitmap.width - side) / 2, (rotatedBitmap.height - side) / 2, side, side)
            if (rotatedBitmap != squared) {
                rotatedBitmap.recycle()
                rotatedBitmap = squared
            }
        }

        val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // MARCA DE AGUA VISUAL PERICIAL
        drawOverlay(mutableBitmap, location, sigpacRef, targetFolders.firstOrNull(), overlayOptions)

        val safeSigpacRef = sigpacRef?.replace(":", "_") ?: "SIN_REFERENCIA"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "${safeSigpacRef}-$timestamp.jpg"
        
        val resultMap = mutableMapOf<String, Uri>()
        val resolver = context.contentResolver

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
                    
                    // SELLADO EXIF DE ALTA FIABILIDAD
                    resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                        val exifFinal = ExifInterface(fd.fileDescriptor)
                        writeExifData(exifFinal, location, sigpacRef, folderName)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    resultMap[folderName] = uri
                } catch (e: Exception) { Log.e("Camera", "Save failed", e) }
            }
        }

        mutableBitmap.recycle()
        rotatedBitmap.recycle()
        bitmap.recycle()
        return resultMap
    }

    /**
     * Inyecta metadatos EXIF con validez pericial (Doble certificación horaria y precisión técnica).
     */
    private fun writeExifData(exif: ExifInterface, location: Location?, sigpacRef: String?, projectName: String): Boolean {
        try {
            val now = Date()
            val localFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())

            // 1. HORA LOCAL (Reloj Sistema)
            val timeLocal = localFormat.format(now)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timeLocal)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, timeLocal)

            // 2. DATOS GPS Y CERTIFICACIÓN SATELITAL
            if (location != null) {
                // Coordenadas DMS Racionales
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDMS(location.latitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDMS(location.longitude))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")

                // Altitud
                val alt = Math.abs(location.altitude)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(alt * 100).toInt()}/100")
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (location.altitude >= 0) "0" else "1")

                // CALIDAD TÉCNICA (Certificación SIGPAC)
                exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "${(location.accuracy * 100).toInt()}/100")
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS_HARDWARE_FIX")
                
                if (location.hasBearing()) {
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(location.bearing * 100).toInt()}/100")
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M") // Magnetic North
                }

                // HORA SATELITAL (Inviolable - UTC)
                val satelliteTime = Date(location.time)
                val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(satelliteTime))
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(satelliteTime))
            }

            // 3. DATOS DISPOSITIVO E INSPECCIÓN
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoSIGPAC Pericial Engine v1.0")
            
            val cleanRef = sigpacRef ?: "N/D"
            val comment = "REF:$cleanRef|PROJ:$projectName|ACC:${location?.accuracy ?: 0f}m"
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment)

            exif.saveAttributes()
            return true
        } catch (e: Exception) {
            Log.e("EXIF", "Error sealing metadata", e)
            return false
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
        val minDim = min(bitmap.width, bitmap.height).toFloat()
        val baseTextSize = minDim * 0.026f
        
        val paintText = Paint().apply {
            color = AndroidColor.WHITE
            textSize = baseTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
            isAntiAlias = true
        }
        
        val paintBg = Paint().apply {
            color = AndroidColor.BLACK
            alpha = 160
        }
        
        val lines = mutableListOf<Pair<String, Int>>()
        
        // 1. FECHA LOCAL CON SEGUNDOS
        val timeLocal = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        lines.add(timeLocal to AndroidColor.WHITE)

        // 2. DISPOSITIVO
        lines.add("DEVICE: ${Build.MODEL}" to AndroidColor.LTGRAY)

        // 3. PRECISIÓN Y GPS
        if (location != null) {
            val accStr = String.format(Locale.US, "ERROR: ±%.1f m", location.accuracy)
            lines.add(accStr to if (location.accuracy < 5) AndroidColor.GREEN else AndroidColor.YELLOW)
            
            val coords = String.format(Locale.US, "LAT:%.6f LNG:%.6f", location.latitude, location.longitude)
            lines.add(coords to AndroidColor.CYAN)
        } else {
            lines.add("GPS: SIN SEÑAL SATÉLITE" to AndroidColor.RED)
        }

        // 4. DATOS SIGPAC
        if (sigpacRef != null) {
            lines.add("REF: $sigpacRef" to AndroidColor.YELLOW)
        }

        if (projectName != null && projectName != "SIN PROYECTO") {
            lines.add("PROJ: $projectName" to AndroidColor.GREEN)
        }

        val lineHeight = baseTextSize * 1.4f
        val padding = baseTextSize * 0.7f
        val boxH = (lineHeight * lines.size) + (padding * 2)
        var maxW = 0f
        lines.forEach { maxW = max(maxW, paintText.measureText(it.first)) }
        val boxW = maxW + (padding * 2)

        canvas.drawRect(0f, bitmap.height - boxH, boxW, bitmap.height.toFloat(), paintBg)
        
        var currentY = bitmap.height - boxH + padding + baseTextSize
        lines.forEach { (text, color) ->
            paintText.color = color
            canvas.drawText(text, padding, currentY, paintText)
            currentY += lineHeight
        }
    }

    private fun toDMS(coordinate: Double): String {
        var d = Math.abs(coordinate)
        val degrees = d.toInt()
        d = (d - degrees) * 60
        val minutes = d.toInt()
        d = (d - minutes) * 60
        val seconds = (d * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}
