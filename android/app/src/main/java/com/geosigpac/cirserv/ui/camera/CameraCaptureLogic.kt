
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

object CameraCaptureLogic {

    fun takePhoto(
        context: Context,
        imageCapture: ImageCapture?,
        projectId: String?,
        sigpacRef: String?,
        location: Location?, // Si es null, no se guarda GPS (respetando config)
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
                            val finalUri = processImageWithOverlay(context, tempFile, projectId, sigpacRef, location)
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

    private fun processImageWithOverlay(
        context: Context,
        sourceFile: File,
        projectId: String?,
        sigpacRef: String?,
        location: Location?
    ): Uri {
        // 1. Cargar y Rotar
        val exifOriginal = ExifInterface(sourceFile.absolutePath)
        val orientation = exifOriginal.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 2. Dibujar Overlay
        drawOverlay(mutableBitmap, location, sigpacRef)
        
        // 3. Guardar en MediaStore
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
        
        // 4. Inyectar EXIF GPS (Solo si location != null)
        if (location != null) {
            try {
                resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                    val exifFinal = ExifInterface(fd.fileDescriptor)
                    val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    
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
                    
                    exifFinal.setAttribute(ExifInterface.TAG_DATETIME, dateFormat.format(Date()))
                    exifFinal.saveAttributes()
                }
            } catch (e: Exception) {
                Log.e("Camera", "Failed to write EXIF GPS", e)
            }
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

    private fun drawOverlay(bitmap: Bitmap, location: Location?, sigpacRef: String?) {
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
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val locStr = if (location != null) {
            "Lat: ${String.format(Locale.US, "%.6f", location.latitude)}  Lng: ${String.format(Locale.US, "%.6f", location.longitude)}"
        } else {
            "Sin Ubicación GPS"
        }
        val refStr = sigpacRef ?: "REF: PENDIENTE / SIN DATOS"
        
        val lineHeight = paintText.textSize * 1.4f
        val padding = lineHeight * 0.5f
        val boxHeight = (lineHeight * 3) + (padding * 2)
        
        canvas.drawRect(0f, h - boxHeight, w.toFloat(), h.toFloat(), paintBg)
        
        val startX = padding
        var startY = h - boxHeight + padding + lineHeight - (lineHeight * 0.2f)
        
        paintText.color = AndroidColor.GREEN
        canvas.drawText("FECHA: $dateStr", startX, startY, paintText)
        
        startY += lineHeight
        paintText.color = AndroidColor.WHITE
        canvas.drawText(locStr, startX, startY, paintText)
        
        startY += lineHeight
        paintText.color = AndroidColor.YELLOW
        canvas.drawText("SIGPAC: $refStr", startX, startY, paintText)
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
