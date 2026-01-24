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
import android.media.ExifInterface
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object CameraCaptureLogic {

    fun takePhoto(
        context: Context,
        imageCapture: ImageCapture?,
        projectId: String?,
        sigpacRef: String?,
        location: Location?,
        cropToSquare: Boolean,
        overlayOptions: Set<OverlayOption>,
        onImageCaptured: (Uri) -> Unit,
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
                            val finalUri = processImage(context, tempFile, projectId, sigpacRef, location, cropToSquare, overlayOptions)
                            tempFile.delete()
                            withContext(Dispatchers.Main) {
                                onImageCaptured(finalUri)
                            }
                        } catch (e: Exception) {
                            Log.e("Camera", "Error processing image", e)
                            withContext(Dispatchers.Main) {
                                onError(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Error procesando overlay: ${e.message}", e))
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
        projectId: String?,
        sigpacRef: String?,
        location: Location?,
        cropToSquare: Boolean,
        overlayOptions: Set<OverlayOption>
    ): Uri {
        // 1. Cargar y Corregir Orientación
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
            val side = Math.min(w, h)
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
            drawOverlay(mutableBitmap, location, sigpacRef, projectId, overlayOptions)
        }
        
        // 4. Guardar en MediaStore
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

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) 
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        // 5. Inyectar TODOS los metadatos EXIF posibles
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                val exifFinal = ExifInterface(fd.fileDescriptor)
                val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val now = Date()

                // A. GPS (Mandatorio)
                if (location != null) {
                    val lat = location.latitude
                    val latRef = if (lat > 0) "N" else "S"
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDMS(lat))
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                    
                    val lon = location.longitude
                    val lonRef = if (lon > 0) "E" else "W"
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDMS(lon))
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
                    
                    val alt = location.altitude
                    val altRef = if (alt < 0) "1" else "0"
                    val altNum = Math.abs(alt) * 1000
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${altNum.toInt()}/1000")
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)

                    // GPS Date/Time
                    val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.getDefault())
                    val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(now))
                    exifFinal.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(now))
                }

                // B. Fechas
                val dateStr = dateFormat.format(now)
                exifFinal.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                exifFinal.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                exifFinal.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)

                // C. Dispositivo
                exifFinal.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                exifFinal.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
                exifFinal.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoSIGPAC App")

                // D. Datos SIGPAC (Trazabilidad)
                val cleanRef = sigpacRef ?: "N/D"
                val cleanProj = projectId ?: "N/D"
                val userComment = "REF:$cleanRef|PROJ:$cleanProj"
                exifFinal.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
                exifFinal.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Inspección SIGPAC Ref: $cleanRef")

                exifFinal.saveAttributes()
            }
        } catch (e: Exception) {
            Log.e("Camera", "Failed to write EXIF", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        mutableBitmap.recycle()
        rotatedBitmap.recycle()
        bitmap.recycle()
        
        return uri
    }

    private fun drawOverlay(
        bitmap: Bitmap, 
        location: Location?, 
        sigpacRef: String?, 
        projectId: String?,
        options: Set<OverlayOption>
    ) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        
        val paintText = Paint().apply {
            color = AndroidColor.WHITE
            textSize = h * 0.022f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, AndroidColor.BLACK)
            isAntiAlias = true
        }
        
        val paintBg = Paint().apply {
            color = AndroidColor.BLACK
            alpha = 140
            style = Paint.Style.FILL
        }
        
        // Calcular líneas a dibujar
        val lines = mutableListOf<Pair<String, Int>>()
        
        if (options.contains(OverlayOption.DATE)) {
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            lines.add("FECHA: $dateStr" to AndroidColor.GREEN)
        }
        
        if (options.contains(OverlayOption.COORDS)) {
            val locStr = if (location != null) {
                "LAT: ${String.format(Locale.US, "%.6f", location.latitude)}  LNG: ${String.format(Locale.US, "%.6f", location.longitude)}"
            } else {
                "GPS: SIN SEÑAL"
            }
            lines.add(locStr to AndroidColor.WHITE)
        }
        
        if (options.contains(OverlayOption.REF)) {
            val refStr = sigpacRef ?: "REF: PENDIENTE / SIN DATOS"
            lines.add("SIGPAC: $refStr" to AndroidColor.YELLOW)
        }

        if (options.contains(OverlayOption.PROJECT) && projectId != null) {
            lines.add("ID: $projectId" to AndroidColor.CYAN)
        }

        if (lines.isEmpty()) return

        val lineHeight = paintText.textSize * 1.4f
        val padding = lineHeight * 0.5f
        val boxHeight = (lineHeight * lines.size) + (padding * 2)
        
        // Calcular el ancho máximo del texto para dimensionar la caja de fondo
        var maxTextWidth = 0f
        lines.forEach { (text, _) ->
            val textWidth = paintText.measureText(text)
            if (textWidth > maxTextWidth) maxTextWidth = textWidth
        }
        val boxWidth = maxTextWidth + (padding * 2)
        
        // Dibujar Fondo (Limitado al ancho del texto)
        canvas.drawRect(0f, h - boxHeight, boxWidth, h.toFloat(), paintBg)
        
        // Dibujar Líneas
        val startX = padding
        var startY = h - boxHeight + padding + lineHeight - (lineHeight * 0.2f)
        
        lines.forEach { (text, color) ->
            paintText.color = color
            canvas.drawText(text, startX, startY, paintText)
            startY += lineHeight
        }
    }

    private fun toDMS(coordinate: Double): String {
        var loc = coordinate
        if (loc < 0) loc = -loc
        val degrees = loc.toInt()
        loc = (loc - degrees) * 60
        val minutes = loc.toInt()
        loc = (loc - minutes) * 60
        val seconds = (loc * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }
}