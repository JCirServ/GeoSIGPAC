
package com.geosigpac.cirserv.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun LocationOverlay(
    isLandscape: Boolean,
    locationText: String,
    activeRef: String?,
    sigpacUso: String?,
    matchedParcelInfo: Any?,
    showNoDataMessage: Boolean,
    onClearManual: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // PANEL IZQUIERDO: RUMBO Y ORIENTACIÓN (HUD PERICIAL)
        Box(
            modifier = if (isLandscape) {
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            } else {
                Modifier.align(Alignment.TopStart).padding(top = 150.dp, start = 16.dp)
            }
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Explore, null, tint = Color.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                // Nota: En una impl real, aquí conectaríamos el sensor de rotación
                Text("HUD", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text("N 12°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // PANEL DERECHO: DATOS SIGPAC (Existente)
        val alignment = Alignment.TopEnd
        val modifier = if (isLandscape) {
            Modifier.align(alignment).padding(16.dp)
        } else {
            Modifier.align(alignment).padding(top = 40.dp, end = 16.dp)
        }

        Box(modifier = modifier) {
            InfoBox(
                locationText = locationText,
                sigpacRef = activeRef,
                sigpacUso = sigpacUso,
                matchedParcelInfo = matchedParcelInfo,
                showNoDataMessage = showNoDataMessage,
                onClearManual = onClearManual
            )
        }
    }
}
