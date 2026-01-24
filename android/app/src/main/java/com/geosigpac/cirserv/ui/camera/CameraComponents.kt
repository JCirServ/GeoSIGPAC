
package com.geosigpac.cirserv.ui.camera

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NeonGreen = Color(0xFF00FF88)

// ENUMS DE CONFIGURACIÓN
enum class GridMode { OFF, RULE_OF_THIRDS, GOLDEN_RATIO, GRID_4X4 }
enum class CameraQuality { MAX, HIGH, BALANCED }
enum class PhotoFormat { RATIO_4_3, RATIO_16_9, RATIO_1_1, FULL_SCREEN }
enum class OverlayOption(val label: String) {
    DATE("Fecha y Hora"),
    COORDS("Coordenadas GPS"),
    REF("Ref. SIGPAC"),
    PROJECT("Info Proyecto")
}

@Composable
fun InfoBox(
    locationText: String,
    sigpacRef: String?,
    sigpacUso: String?,
    matchedParcelInfo: Any?,
    showNoDataMessage: Boolean
) {
    Box(
        modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(locationText, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.height(6.dp))
            if (sigpacRef != null) {
                Text("Ref: $sigpacRef", color = Color(0xFFFFFF00), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold)
                Text("Uso: ${sigpacUso ?: "N/D"}", color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                
                if (matchedParcelInfo != null) {
                    Text("EN PROYECTO", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            } else if (showNoDataMessage) {
                Text("Sin datos SIGPAC", color = Color(0xFFFFAAAA), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text("Analizando zona...", color = Color.LightGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun ZoomControl(
    currentZoom: Float,
    isLandscape: Boolean,
    onZoomChange: (Float) -> Unit
) {
    val containerModifier = if (isLandscape) {
         Modifier
            .width(260.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
    } else {
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
            value = currentZoom,
            onValueChange = onZoomChange,
            modifier = if (isLandscape) {
                Modifier.width(240.dp)
            } else {
                Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin.Center
                    }
                    .requiredWidth(260.dp)
            }, 
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun ShutterButton(
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(4.dp, NeonGreen, CircleShape)
            .padding(6.dp)
            .background(if(isProcessing) Color.Gray else NeonGreen, CircleShape)
            .clickable(enabled = !isProcessing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    desc: String,
    tint: Color = NeonGreen,
    alpha: Float = 1f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint.copy(alpha = alpha)) }
}

@Composable
fun SquareButton(
    icon: ImageVector,
    desc: String,
    count: Int = 0,
    previewBitmap: ImageBitmap? = null,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(0.5f))
                .border(2.dp, NeonGreen, RoundedCornerShape(24.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { 
            if (previewBitmap != null) {
                Image(bitmap = previewBitmap, contentDescription = desc, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(icon, desc, tint = NeonGreen, modifier = Modifier.size(36.dp)) 
            }
        }
        if (count > 0) {
            Box(
                modifier = Modifier.offset(x = 8.dp, y = (-8).dp).size(28.dp).background(Color.Red, CircleShape).border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = count.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GridOverlay(mode: GridMode) {
    if (mode == GridMode.OFF) return
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(0.3f)
        val stroke = 2f

        when (mode) {
            GridMode.RULE_OF_THIRDS -> {
                drawLine(color, Offset(w/3, 0f), Offset(w/3, h), stroke)
                drawLine(color, Offset(2*w/3, 0f), Offset(2*w/3, h), stroke)
                drawLine(color, Offset(0f, h/3), Offset(w, h/3), stroke)
                drawLine(color, Offset(0f, 2*h/3), Offset(w, 2*h/3), stroke)
            }
            GridMode.GRID_4X4 -> {
                drawLine(color, Offset(w/4, 0f), Offset(w/4, h), stroke)
                drawLine(color, Offset(w/2, 0f), Offset(w/2, h), stroke)
                drawLine(color, Offset(3*w/4, 0f), Offset(3*w/4, h), stroke)
                drawLine(color, Offset(0f, h/4), Offset(w, h/4), stroke)
                drawLine(color, Offset(0f, h/2), Offset(w, h/2), stroke)
                drawLine(color, Offset(0f, 3*h/4), Offset(w, 3*h/4), stroke)
            }
            GridMode.GOLDEN_RATIO -> {
                val g1 = 0.382f
                val g2 = 0.618f
                drawLine(color, Offset(w*g1, 0f), Offset(w*g1, h), stroke)
                drawLine(color, Offset(w*g2, 0f), Offset(w*g2, h), stroke)
                drawLine(color, Offset(0f, h*g1), Offset(w, h*g1), stroke)
                drawLine(color, Offset(0f, h*g2), Offset(w, h*g2), stroke)
            }
            else -> {}
        }
    }
}

@Composable
fun SettingsDialog(
    currentFormat: PhotoFormat,
    flashMode: Int,
    gridMode: GridMode,
    quality: CameraQuality,
    selectedOverlays: Set<OverlayOption>,
    onDismiss: () -> Unit,
    onFormatChange: (PhotoFormat) -> Unit,
    onFlashChange: (Int) -> Unit,
    onGridChange: (GridMode) -> Unit,
    onQualityChange: (CameraQuality) -> Unit,
    onOverlayToggle: (OverlayOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración Profesional") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. FORMATO
                Text("Formato de Captura", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currentFormat == PhotoFormat.RATIO_4_3, onClick = { onFormatChange(PhotoFormat.RATIO_4_3) }, label = { Text("4:3") })
                    FilterChip(selected = currentFormat == PhotoFormat.RATIO_16_9, onClick = { onFormatChange(PhotoFormat.RATIO_16_9) }, label = { Text("16:9") })
                    FilterChip(selected = currentFormat == PhotoFormat.RATIO_1_1, onClick = { onFormatChange(PhotoFormat.RATIO_1_1) }, label = { Text("1:1") })
                }
                
                Text("Calidad", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = quality == CameraQuality.MAX, onClick = { onQualityChange(CameraQuality.MAX) }, label = { Text("Máxima") })
                    FilterChip(selected = quality == CameraQuality.HIGH, onClick = { onQualityChange(CameraQuality.HIGH) }, label = { Text("Alta") })
                }

                Divider(color = Color.White.copy(0.1f))

                // 2. FLASH / LINTERNA
                Text("Iluminación", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_AUTO, onClick = { onFlashChange(ImageCapture.FLASH_MODE_AUTO) }); Text("Auto", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_OFF, onClick = { onFlashChange(ImageCapture.FLASH_MODE_OFF) }); Text("Off", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = flashMode == 3, onClick = { onFlashChange(3) }); Text("Antorcha", fontSize = 12.sp)
                    }
                }

                Divider(color = Color.White.copy(0.1f))

                // 3. COMPOSICIÓN
                Text("Guías de Composición", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                         Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = gridMode == GridMode.OFF, onClick = { onGridChange(GridMode.OFF) }); Text("Ninguna", fontSize = 12.sp) }
                         Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = gridMode == GridMode.RULE_OF_THIRDS, onClick = { onGridChange(GridMode.RULE_OF_THIRDS) }); Text("Tercios", fontSize = 12.sp) }
                    }
                    Column {
                         Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = gridMode == GridMode.GRID_4X4, onClick = { onGridChange(GridMode.GRID_4X4) }); Text("4x4", fontSize = 12.sp) }
                         Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = gridMode == GridMode.GOLDEN_RATIO, onClick = { onGridChange(GridMode.GOLDEN_RATIO) }); Text("Áurea", fontSize = 12.sp) }
                    }
                }
                
                Divider(color = Color.White.copy(0.1f))

                // 4. DATOS EN FOTO (OVERLAY)
                Text("Datos sobreimpresionados", style = MaterialTheme.typography.labelMedium, color = NeonGreen)
                OverlayOption.values().forEach { option ->
                    val isSelected = selectedOverlays.contains(option)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onOverlayToggle(option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = { onOverlayToggle(option) })
                        Text(option.label, fontSize = 14.sp)
                    }
                }
                Text("Nota: Los metadatos EXIF completos siempre se guardarán.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

val MapIconVector = ImageVector.Builder(
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
