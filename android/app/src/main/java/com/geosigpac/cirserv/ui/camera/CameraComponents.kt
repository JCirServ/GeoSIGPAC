
package com.geosigpac.cirserv.ui.camera

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawLine(Color.White.copy(0.3f), Offset(w/3, 0f), Offset(w/3, h), 2f)
        drawLine(Color.White.copy(0.3f), Offset(2*w/3, 0f), Offset(2*w/3, h), 2f)
        drawLine(Color.White.copy(0.3f), Offset(0f, h/3), Offset(w, h/3), 2f)
        drawLine(Color.White.copy(0.3f), Offset(0f, 2*h/3), Offset(w, 2*h/3), 2f)
    }
}

@Composable
fun SettingsDialog(
    aspectRatio: Int,
    flashMode: Int,
    showGrid: Boolean,
    onDismiss: () -> Unit,
    onRatioChange: (Int) -> Unit,
    onFlashChange: (Int) -> Unit,
    onGridChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración de Cámara") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Resolución (Aspect Ratio)", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = aspectRatio == AspectRatio.RATIO_4_3, onClick = { onRatioChange(AspectRatio.RATIO_4_3) }); Text("4:3")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = aspectRatio == AspectRatio.RATIO_16_9, onClick = { onRatioChange(AspectRatio.RATIO_16_9) }); Text("16:9")
                }
                Divider()
                Text("Flash", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_AUTO, onClick = { onFlashChange(ImageCapture.FLASH_MODE_AUTO) }); Text("Auto")
                    RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_ON, onClick = { onFlashChange(ImageCapture.FLASH_MODE_ON) }); Text("On")
                    RadioButton(selected = flashMode == ImageCapture.FLASH_MODE_OFF, onClick = { onFlashChange(ImageCapture.FLASH_MODE_OFF) }); Text("Off")
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onGridChange(!showGrid) }) {
                    Checkbox(checked = showGrid, onCheckedChange = onGridChange); Text("Mostrar Cuadrícula")
                }
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
