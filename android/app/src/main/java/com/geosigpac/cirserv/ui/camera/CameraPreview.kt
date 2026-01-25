
package com.geosigpac.cirserv.ui.camera

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(
    previewView: PreviewView,
    photoFormat: PhotoFormat,
    gridMode: GridMode,
    showFocusRing: Boolean,
    focusRingPosition: Offset?,
    onTapToFocus: (Offset) -> Unit,
    onZoomGesture: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    onZoomGesture(zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTapToFocus(offset)
                }
            }
    ) {
        // Vista Nativa CameraX
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                previewView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
        )

        // Anillo de Enfoque
        if (showFocusRing && focusRingPosition != null) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(LocalDensity.current) { focusRingPosition.x.toDp() - 25.dp },
                        y = with(LocalDensity.current) { focusRingPosition.y.toDp() - 25.dp }
                    )
                    .size(50.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
        }

        // Rejilla
        GridOverlay(gridMode)

        // Máscara para formato 1:1
        if (photoFormat == PhotoFormat.RATIO_1_1) {
            Box(modifier = Modifier.fillMaxSize()) {
                val color = Color.Black.copy(0.7f)
                // Barras negras visuales para simular recorte cuadrado
                // Nota: Esto es una aproximación visual simple para la UI
                // El recorte real sucede en la lógica de captura
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(color).align(Alignment.TopCenter))
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(color).align(Alignment.BottomCenter))
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val side = size.width
                    val topOffset = (size.height - side) / 2
                    // Dibuja rectángulos negros arriba y abajo del cuadrado central
                    if (topOffset > 0) {
                        drawRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, topOffset))
                        drawRect(color, Offset(0f, topOffset + side), androidx.compose.ui.geometry.Size(size.width, size.height - (topOffset + side)))
                    }
                }
            }
        }
    }
}
