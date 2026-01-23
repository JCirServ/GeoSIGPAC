
package com.geosigpac.cirserv.ui.components.recinto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.SigpacCodeManager

@Composable
fun RecintoTechnicalData(
    parcela: NativeParcela,
    onLocate: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 0) Color(0xFF00FF88) else Color.Transparent)
                .clickable { selectedTab = 0 }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Recinto", 
                fontWeight = FontWeight.Bold, 
                fontSize = 15.sp,
                color = if(selectedTab == 0) Color.White else Color.Gray
            )
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 1) Color(0xFF62D2FF) else Color.Transparent)
                .clickable { selectedTab = 1 }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Cultivo", 
                fontWeight = FontWeight.Bold, 
                fontSize = 15.sp,
                color = if(selectedTab == 1) Color.Black else Color.Gray
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    
    if (selectedTab == 0) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f)) { DataField("USO SIGPAC", parcela.sigpacInfo?.usoSigpac ?: "-") }
                Box(Modifier.weight(1f)) { DataField("SUPERFICIE", "${parcela.sigpacInfo?.superficie ?: "-"} ha") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f)) { DataField("PENDIENTE", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %") }
                Box(Modifier.weight(1f)) { DataField("ALTITUD", "${parcela.sigpacInfo?.altitud ?: "-"} m") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f)) { DataField("REGIÓN", parcela.sigpacInfo?.region ?: "-") }
                Box(Modifier.weight(1f)) { DataField("COEF. REGADÍO", "${parcela.sigpacInfo?.coefRegadio ?: "-"}") }
            }
            DataField("ADMISIBILIDAD", "${parcela.sigpacInfo?.admisibilidad ?: "-"}")
            
            if (!parcela.sigpacInfo?.incidencias.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                IncidenciasStaticList(parcela.sigpacInfo?.incidencias)
            }
        }
    } else {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f)) { DataField("EXP. NUM", parcela.cultivoInfo?.expNum ?: "-") }
                val sistExpRaw = parcela.cultivoInfo?.parcSistexp
                val sistExpDisplay = when(sistExpRaw) {
                    "S" -> "Secano"
                    "R" -> "Regadío"
                    else -> sistExpRaw ?: "-"
                }
                Box(Modifier.weight(1f)) { DataField("SIST. EXP", sistExpDisplay) }
            }
            
            val prodDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.parcProducto?.toString())
            DataField("PRODUCTO", prodDesc ?: "-")
            
            DataField("SUP. CULT", "${parcela.cultivoInfo?.parcSupcult ?: "-"} m²")

            if (!parcela.cultivoInfo?.parcAyudasol.isNullOrEmpty()) {
                AyudasStaticList("AYUDA SOL", parcela.cultivoInfo?.parcAyudasol)
            } else {
                DataField("AYUDA SOL", "-")
            }

            if (!parcela.cultivoInfo?.pdrRec.isNullOrEmpty()) {
                AyudasStaticList("AYUDAS PDR", parcela.cultivoInfo?.pdrRec, isPdr = true)
            } else {
                DataField("AYUDAS PDR", "-")
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.1f))

            val prodSecDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.cultsecunProducto?.toString())
            DataField("PROD. SEC", prodSecDesc ?: "-")
            
            if (!parcela.cultivoInfo?.cultsecunAyudasol.isNullOrEmpty()) {
                AyudasStaticList("AYUDA SEC", parcela.cultivoInfo?.cultsecunAyudasol)
            } else {
                DataField("AYUDA SEC", "-")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f)) { DataField("IND. CULT", parcela.cultivoInfo?.parcIndcultapro?.toString() ?: "-") }
                Box(Modifier.weight(1f)) { DataField("APROVECHA", parcela.cultivoInfo?.tipoAprovecha ?: "-") }
            }
        }
    }
    
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { 
            // Usamos el ID interno para localizar usando geometría local, evitando la API externa
            onLocate("LOC:${parcela.id}")
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("LOCALIZAR EN MAPA", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
