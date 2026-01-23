
package com.geosigpac.cirserv.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
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
fun NativeRecintoCard(
    parcela: NativeParcela,
    onLocate: (String) -> Unit,
    onCamera: (String) -> Unit,
    onUpdateParcela: (NativeParcela) -> Unit,
    initiallyExpanded: Boolean = false,
    initiallyTechExpanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    
    // Colores Neon
    val NeonGreen = Color(0xFF00FF88)
    val DarkSurface = Color(0xFF1E1E1E)
    val CardBorder = Color.White.copy(0.1f)

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = parcela.referencia,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (parcela.uso.isNotEmpty()) {
                            Text(
                                text = "Uso: ${parcela.uso} • ${String.format("%.4f ha", parcela.area)}",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onCamera(parcela.id) },
                    modifier = Modifier
                        .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                        .size(36.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                }
            }

            // Expandable Content
            if (isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    
                    // Análisis de Compatibilidad
                    if (parcela.sigpacInfo != null && parcela.cultivoInfo != null) {
                        val analysis = remember(parcela) {
                            SigpacCodeManager.performAgroAnalysis(
                                productoCode = parcela.cultivoInfo.parcProducto,
                                productoDesc = null,
                                sigpacUso = parcela.sigpacInfo.usoSigpac,
                                ayudasRaw = parcela.cultivoInfo.parcAyudasol,
                                pdrRaw = parcela.cultivoInfo.pdrRec,
                                sistExp = parcela.cultivoInfo.parcSistexp,
                                coefRegadio = parcela.sigpacInfo.coefRegadio,
                                pendienteMedia = parcela.sigpacInfo.pendienteMedia
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (analysis.isCompatible) NeonGreen.copy(0.1f) else Color(0xFFFF5252).copy(0.1f))
                                .border(1.dp, if (analysis.isCompatible) NeonGreen.copy(0.3f) else Color(0xFFFF5252).copy(0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (analysis.isCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (analysis.isCompatible) NeonGreen else Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (analysis.isCompatible) "Compatible" else "Posible Incidencia",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis.isCompatible) NeonGreen else Color(0xFFFF5252)
                                )
                                if (analysis.explanation.isNotEmpty()) {
                                    Text(
                                        text = analysis.explanation,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Datos Técnicos (Tabs o Columnas)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // SIGPAC
                        Column(modifier = Modifier.weight(1f).background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text("SIGPAC", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                            if (parcela.sigpacInfo != null) {
                                TechRow("Uso", parcela.sigpacInfo.usoSigpac)
                                TechRow("Pendiente", "${parcela.sigpacInfo.pendienteMedia}%")
                                TechRow("Coef. Reg", "${parcela.sigpacInfo.coefRegadio}%")
                                TechRow("Región", parcela.sigpacInfo.region)
                            } else {
                                Text("Sin datos", color = Color.Gray, fontSize = 11.sp)
                            }
                        }

                        // DECLARACIÓN
                        Column(modifier = Modifier.weight(1f).background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text("DECLARACIÓN", color = Color(0xFFFFB74D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                            if (parcela.cultivoInfo != null) {
                                TechRow("Producto", "${parcela.cultivoInfo.parcProducto}")
                                TechRow("Sist. Exp", parcela.cultivoInfo.parcSistexp)
                                TechRow("Ayudas", if (parcela.cultivoInfo.parcAyudasol.isNullOrEmpty()) "No" else "Sí")
                            } else {
                                Text("Sin datos", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onLocate(parcela.referencia) },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("LOCALIZAR EN MAPA", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TechRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(
            text = value ?: "-",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
