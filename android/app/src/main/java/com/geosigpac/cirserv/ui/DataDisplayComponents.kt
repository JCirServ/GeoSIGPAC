
package com.geosigpac.cirserv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.utils.SigpacCodeManager

@Composable
fun CollapsibleHeader(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray)
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null,
            tint = Color.Gray
        )
    }
}

@Composable
fun IncidenciasStaticList(rawIncidencias: String?) {
    val incidenciasList = remember(rawIncidencias) {
        SigpacCodeManager.getFormattedIncidencias(rawIncidencias)
    }

    if (incidenciasList.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFF5252).copy(0.3f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "INCIDENCIAS (${incidenciasList.size})",
                color = Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical=6.dp))
            incidenciasList.forEach { incidencia ->
                Text(
                    text = "• $incidencia",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun AyudasStaticList(label: String, rawAyudas: String?, isPdr: Boolean = false) {
    val ayudasList = remember(rawAyudas, isPdr) {
        if (isPdr) SigpacCodeManager.getFormattedAyudasPdr(rawAyudas)
        else SigpacCodeManager.getFormattedAyudas(rawAyudas)
    }

    if (ayudasList.isNotEmpty()) {
         Column(modifier = Modifier.padding(vertical = 6.dp)) {
             Text(label, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
             Column(modifier = Modifier.padding(top = 4.dp)) {
                 ayudasList.forEach { ayuda ->
                     Text(
                        text = "• $ayuda",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                 }
             }
         }
    } else {
        DataField(label, "-")
    }
}

@Composable
fun DataField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
