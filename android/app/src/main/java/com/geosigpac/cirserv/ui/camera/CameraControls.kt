
package com.geosigpac.cirserv.ui.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun CameraControls(
    isLandscape: Boolean,
    isProcessingImage: Boolean,
    currentZoom: Float,
    // Estado Info Botón
    matchedParcelInfo: Any?, // Usamos Any? para no acoplar el tipo Pair complejo aquí, solo chequeamos nullidad
    isInsideGeometry: Boolean,
    // Botones Preview
    photoCount: Int,
    capturedBitmap: ImageBitmap?,
    // Callbacks
    onSettingsClick: () -> Unit,
    onProjectsClick: () -> Unit,
    onManualClick: () -> Unit,
    onInfoClick: () -> Unit,
    onShutterClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onMapClick: () -> Unit,
    onZoomChange: (Float) -> Unit
) {
    // Animación de parpadeo para el botón Info
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
        label = "Blink"
    )

    if (isLandscape) {
        // --- LANDSCAPE LAYOUT ---
        // Top Left Controls
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlButton(Icons.Default.Settings, "Config") { onSettingsClick() }
                    ControlButton(Icons.Default.List, "Proyectos") { onProjectsClick() }
                    ControlButton(Icons.Default.Keyboard, "Manual") { onManualClick() }
                    
                    if (matchedParcelInfo != null) {
                        ControlButton(
                            Icons.Default.Info,
                            "Info",
                            tint = if (isInsideGeometry) NeonGreen else Color.White,
                            alpha = if (isInsideGeometry) blinkAlpha else 1f
                        ) { onInfoClick() }
                    }
                }
            }

            // Shutter (Right Center)
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
                ShutterButton(isProcessingImage) { onShutterClick() }
            }

            // Bottom Left (Preview & Map) - Desplazados para no tapar el Mini Mapa
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 160.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                SquareButton(Icons.Default.Image, "Preview", photoCount, capturedBitmap) { onPreviewClick() }
                SquareButton(MapIconVector, "Map") { onMapClick() }
            }

            // Bottom Right (Zoom)
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 32.dp)) {
                ZoomControl(currentZoom, true, onZoomChange)
            }
        }
    } else {
        // --- PORTRAIT LAYOUT ---
        Box(modifier = Modifier.fillMaxSize()) {
            // Top Left Controls - Desplazados debajo del Mini Mapa
            // El mapa mide aprox 130dp + 40dp top padding. Empezamos en 180dp.
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 180.dp, start = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlButton(Icons.Default.Settings, "Config") { onSettingsClick() }
                    ControlButton(Icons.Default.List, "Proyectos") { onProjectsClick() }
                    ControlButton(Icons.Default.Keyboard, "Manual") { onManualClick() }
                    
                    if (matchedParcelInfo != null) {
                        ControlButton(
                            Icons.Default.Info,
                            "Info",
                            tint = if (isInsideGeometry) NeonGreen else Color.White,
                            alpha = if (isInsideGeometry) blinkAlpha else 1f
                        ) { onInfoClick() }
                    }
                }
            }

            // Left Bottom (Zoom) - Desplazado al fondo para no solapar controles ni mapa
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 120.dp)) {
                ZoomControl(currentZoom, false, onZoomChange)
            }

            // Bottom Bar
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SquareButton(Icons.Default.Image, "Preview", photoCount, capturedBitmap) { onPreviewClick() }
                    ShutterButton(isProcessingImage) { onShutterClick() }
                    SquareButton(MapIconVector, "Map") { onMapClick() }
                }
            }
        }
    }
}
