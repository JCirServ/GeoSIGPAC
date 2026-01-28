
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
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CameraCaptureLogic {

    private const val UPLOAD_TIMEOUT_MS = 120000L // 120s Timeout

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
        // 1. Cargar y Corregir Orientación (Solo una vez)
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
        
        // 3. Dibujar Overlay Configurable (Solo una vez en el bitmap final)
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
                    
                    // 5. Inyectar Metadatos EXIF
                    resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                        val exifFinal = ExifInterface(fd.fileDescriptor)
                        writeExifData(exifFinal, location, sigpacRef, folderName)
                        exifFinal.saveAttributes()
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    
                    resultMap[folderName] = uri
                } catch (e: Exception) {
                    Log.e("Camera", "Failed to save for project $folderName", e)
                }
            }
        }

        mutableBitmap.recycle()
        rotatedBitmap.recycle()
        bitmap.recycle()
        
        return resultMap
    }

    private fun writeExifData(exif: ExifInterface, location: Location?, sigpacRef: String?, projectName: String) {
        val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val now = Date()

        // A. GPS
        if (location != null) {
            val lat = location.latitude
            val latRef = if (lat > 0) "N" else "S"
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDMS(lat))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
            
            val lon = location.longitude
            val lonRef = if (lon > 0) "E" else "W"
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDMS(lon))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
            
            val alt = location.altitude
            val altRef = if (alt < 0) "1" else "0"
            val altNum = Math.abs(alt) * 1000
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${altNum.toInt()}/1000")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)

            val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.getDefault())
            val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(now))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(now))
        }

        // B. Fechas y Dispositivo
        val dateStr = dateFormat.format(now)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoSIGPAC App")

        // C. Datos SIGPAC
        val cleanRef = sigpacRef ?: "N/D"
        val userComment = "REF:$cleanRef|PROJ:$projectName"
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Inspección SIGPAC Ref: $cleanRef Proyecto: $projectName")
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
        
        // --- CÁLCULO DE PROPORCIONES ---
        // Usamos la dimensión más pequeña (minDimension) como base.
        // Esto garantiza que el texto se vea del mismo tamaño relativo en Vertical (Portrait) y Horizontal (Landscape).
        val minDimension = min(w, h).toFloat()
        
        // Tamaño base del texto: 2.8% del lado más corto.
        // Ej: En una foto de 3000x4000, min=3000 -> text=84px.
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

        if (options.contains(OverlayOption.PROJECT) && projectName != null) {
            lines.add("PROY: $projectName" to AndroidColor.CYAN)
        }

        if (lines.isEmpty()) return

        // --- CÁLCULO DE CAJETÍN ---
        val lineHeight = baseTextSize * 1.45f
        val padding = baseTextSize * 0.8f // Padding relativo al tamaño de fuente
        
        val boxHeight = (lineHeight * lines.size) + (padding * 2)
        
        var maxTextWidth = 0f
        lines.forEach { (text, _) ->
            val textWidth = paintText.measureText(text)
            if (textWidth > maxTextWidth) maxTextWidth = textWidth
        }
        val boxWidth = maxTextWidth + (padding * 2)
        
        // Dibujamos el fondo en la esquina inferior izquierda
        canvas.drawRect(0f, h - boxHeight, boxWidth, h.toFloat(), paintBg)
        
        val startX = padding
        // Ajuste fino vertical para centrar el texto en sus líneas
        var startY = h - boxHeight + padding + baseTextSize 
        
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
