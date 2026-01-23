
package com.geosigpac.cirserv.ui.components.recinto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.AgroAnalysisResult

@Composable
fun RecintoHeader(
    parcela: NativeParcela,
    isLoading: Boolean,
    isFullyCompleted: Boolean,
    photosEnough: Boolean,
    agroAnalysis: AgroAnalysisResult?,
    statusColor: Color,
    statusIcon: ImageVector?,
    onExpand: () -> Unit,
    onCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if(isLoading) Color.Yellow else statusColor))
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(parcela.referencia, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            if (isLoading) {
                Text("Cargando atributos...", color = Color.Gray, fontSize = 14.sp)
            } else if (isFullyCompleted) {
                Text("VERIFICADO: ${parcela.finalVerdict?.replace("_", " ")}", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else if (!photosEnough) {
                Text("FALTAN FOTOS (${parcela.photos.size}/2)", color = Color(0xFF62D2FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else if (agroAnalysis != null) {
                 val alertText = if(!agroAnalysis.isCompatible) "INCOMPATIBLE / ERROR" else "PENDIENTE DE VERIFICACIÓN"
                 Text(alertText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!isLoading) {
            if (statusIcon != null) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(24.dp).padding(end = 8.dp))
            }
            IconButton(onClick = onCamera) { 
                Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(24.dp)) 
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Yellow)
        }
    }
}
