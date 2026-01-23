
package com.geosigpac.cirserv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.SigpacCodeManager
import java.util.Locale

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
    
    // Colores del Tema
    val NeonGreen = Color(0xFF00FF88)
    val WarningOrange = Color(0xFFF97316)
    val ErrorRed = Color(0xFFFF5252)
    val DarkSurface = Color(0xFF1E1E1E)
    val CardBorder = Color.White.copy(0.1f)
    val SectionHeaderColor = Color(0xFF64B5F6) // Azul claro
    val DeclarationColor = Color(0xFFFFB74D) // Naranja claro

    // PREPARACIÓN DE DATOS (Decodificación)
    val usoDesc = remember(parcela.uso) { SigpacCodeManager.getUsoDescription(parcela.uso) ?: parcela.uso }
    
    // Análisis Agroambiental
    val analysis = remember(parcela) {
        if (parcela.sigpacInfo != null && parcela.cultivoInfo != null) {
            SigpacCodeManager.performAgroAnalysis(
                productoCode = parcela.cultivoInfo.parcProducto,
                productoDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo.parcProducto?.toString()),
                sigpacUso = parcela.sigpacInfo.usoSigpac,
                ayudasRaw = parcela.cultivoInfo.parcAyudasol,
                pdrRaw = parcela.cultivoInfo.pdrRec,
                sistExp = parcela.cultivoInfo.parcSistexp,
                coefRegadio = parcela.sigpacInfo.coefRegadio,
                pendienteMedia = parcela.sigpacInfo.pendienteMedia
            )
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            // --- HEADER ---
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
                            fontSize = 15.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = "$usoDesc • ${String.format(Locale.US, "%.4f ha", parcela.area)}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge de Fotos
                    val photoCount = parcela.photos.size
                    if (photoCount > 0) {
                        Badge(containerColor = NeonGreen, contentColor = Color.Black) { Text("$photoCount") }
                        Spacer(Modifier.width(8.dp))
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
            }

            // --- CONTENIDO EXPANDIBLE ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    
                    // 1. ANÁLISIS DE COMPATIBILIDAD (Bloque Superior)
                    if (analysis != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (analysis.isCompatible) NeonGreen.copy(0.1f) else ErrorRed.copy(0.1f))
                                .border(1.dp, if (analysis.isCompatible) NeonGreen.copy(0.3f) else ErrorRed.copy(0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (analysis.isCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (analysis.isCompatible) NeonGreen else ErrorRed,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (analysis.isCompatible) "COMPATIBLE" else "POSIBLE INCIDENCIA",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (analysis.isCompatible) NeonGreen else ErrorRed,
                                    letterSpacing = 1.sp
                                )
                                if (analysis.explanation.isNotEmpty()) {
                                    Text(
                                        text = analysis.explanation,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        // 1.5 GUÍA DE CAMPO (REQUISITOS ESPECÍFICOS)
                        if (analysis.requirements.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Assignment, null, tint = DeclarationColor, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("REQUISITOS A VERIFICAR", color = DeclarationColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(4.dp))
                                analysis.requirements.forEach { req ->
                                    Text("• ${req.description}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("  ${req.requirement}", color = Color.Gray, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 2. GRID DE DATOS TÉCNICOS
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        
                        // COLUMNA IZQUIERDA: SIGPAC (Oficial)
                        Column(modifier = Modifier.weight(1f).background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text("SIGPAC", color = SectionHeaderColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = SectionHeaderColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (parcela.sigpacInfo != null) {
                                val info = parcela.sigpacInfo
                                TechRow("Uso", SigpacCodeManager.getUsoDescription(info.usoSigpac) ?: info.usoSigpac)
                                TechRow("Región", SigpacCodeManager.getRegionDescription(info.region))
                                TechRow("Coef. Reg", "${info.coefRegadio ?: 0}%")
                                TechRow("Pendiente", "${info.pendienteMedia ?: 0}%")
                                
                                // Incidencias SIGPAC
                                if (!info.incidencias.isNullOrEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Incidencias:", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    SigpacCodeManager.getFormattedIncidencias(info.incidencias).forEach { 
                                        Text("• $it", color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                                    }
                                }
                            } else {
                                Text("Sin datos cargados", color = Color.Gray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }

                        // COLUMNA DERECHA: DECLARACIÓN (Solicitud)
                        Column(modifier = Modifier.weight(1f).background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text("DECLARACIÓN", color = DeclarationColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = DeclarationColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (parcela.cultivoInfo != null) {
                                val cult = parcela.cultivoInfo
                                
                                // Producto Decodificado
                                val prodDesc = SigpacCodeManager.getProductoDescription(cult.parcProducto?.toString()) ?: cult.parcProducto?.toString()
                                TechRow("Producto", prodDesc, highlight = true)
                                
                                // Sistema Explotación Decodificado
                                val sistLabel = when(cult.parcSistexp?.uppercase()) {
                                    "S" -> "Secano"; "R" -> "Regadío"; else -> cult.parcSistexp
                                }
                                TechRow("Sistema", sistLabel)

                                // Ayudas Solicitadas Detalladas
                                if (!cult.parcAyudasol.isNullOrEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Ayudas Directas:", color = DeclarationColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    SigpacCodeManager.getFormattedAyudas(cult.parcAyudasol).forEach {
                                        Text("• $it", color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                                    }
                                }

                                // Ayudas PDR Detalladas
                                if (!cult.pdrRec.isNullOrEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Ayudas PDR:", color = NeonGreen.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    SigpacCodeManager.getFormattedAyudasPdr(cult.pdrRec).forEach {
                                        Text("• $it", color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                                    }
                                }
                            } else {
                                Text("Sin declaración", color = Color.Gray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 3. BOTÓN DE ACCIÓN (Localizar)
                    Button(
                        onClick = { onLocate(parcela.referencia) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("VER EN MAPA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TechRow(label: String, value: String?, highlight: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.Gray, fontSize = 10.sp, lineHeight = 10.sp)
        Text(
            text = value ?: "-",
            color = if (highlight) Color(0xFFFFB74D) else Color.White,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Black else FontWeight.Bold,
            lineHeight = 14.sp
        )
    }
}
