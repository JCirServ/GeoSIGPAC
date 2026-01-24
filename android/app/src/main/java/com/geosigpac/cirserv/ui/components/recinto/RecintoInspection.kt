
package com.geosigpac.cirserv.ui.components.recinto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
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
import com.geosigpac.cirserv.utils.AgroAnalysisResult
import com.geosigpac.cirserv.utils.SigpacCodeManager

@Composable
fun RecintoInspection(
    parcela: NativeParcela,
    agroAnalysis: AgroAnalysisResult,
    photosEnough: Boolean,
    onUpdateParcela: (NativeParcela) -> Unit
) {
    var showUsoDropdown by remember { mutableStateOf(false) }
    var showProductoDropdown by remember { mutableStateOf(false) }
    
    // Listas cargadas
    val allUsos = remember { SigpacCodeManager.getAllUsos() }
    val allProductos = remember { SigpacCodeManager.getAllProductos() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(0.3f))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
         Column {
             val explanation = agroAnalysis.explanation
             val isWarning = explanation.contains("AVISO", ignoreCase = true) || explanation.contains("Nota", ignoreCase = true)
             val isIrrigationError = explanation.contains("ERROR RIEGO", ignoreCase = true)
             val isUseIncompatible = !agroAnalysis.isCompatible && !isIrrigationError
             
             // Si el sistema lo ha auto-verificado (por compatibilidad), mostramos que está activo.
             val hasUsoOverride = parcela.verifiedUso != null
             val hasProdOverride = parcela.verifiedProductoCode != null
             
             // 1. Mensajes de Aviso/Error
             if (!agroAnalysis.isCompatible || isWarning) {
                 val title = if (!agroAnalysis.isCompatible) "DISCREPANCIA DETECTADA" else "AVISO DE COHERENCIA"
                 val color = if (!agroAnalysis.isCompatible) Color(0xFFFF5252) else Color(0xFFFF9800)

                 Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
                 Spacer(Modifier.height(4.dp))
                 Text(agroAnalysis.explanation, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                 Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical=8.dp))
             }

             if (isIrrigationError) {
                 Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                     Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                     Spacer(Modifier.width(8.dp))
                     Text("Comprobar documentalmente", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                 }
             }

             // ----------------------------
             // 2A. SELECTOR DE USO SIGPAC
             // ----------------------------
             Text("USO SUELO OBSERVADO:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
             Spacer(Modifier.height(4.dp))
             
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(
                     imageVector = if(hasUsoOverride) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                     contentDescription = null,
                     tint = if(hasUsoOverride) Color(0xFF00FF88) else Color.Gray,
                     modifier = Modifier.size(24.dp).clickable {
                         if (hasUsoOverride) onUpdateParcela(parcela.copy(verifiedUso = null))
                         else showUsoDropdown = true
                     }
                 )
                 Spacer(Modifier.width(12.dp))
                 
                 Box(modifier = Modifier.weight(1f)) {
                     OutlinedButton(
                         onClick = { showUsoDropdown = true },
                         modifier = Modifier.fillMaxWidth(),
                         colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
                         border = BorderStroke(1.dp, if(hasUsoOverride) Color(0xFF00FF88) else Color.Gray)
                     ) {
                         Text(
                             text = if(parcela.verifiedUso != null) "USO: ${parcela.verifiedUso}" else "SELECCIONAR USO...", 
                             fontSize = 13.sp,
                             fontWeight = if(hasUsoOverride) FontWeight.Bold else FontWeight.Normal
                         )
                     }
                     
                     DropdownMenu(
                         expanded = showUsoDropdown,
                         onDismissRequest = { showUsoDropdown = false },
                         modifier = Modifier.background(MaterialTheme.colorScheme.surface).heightIn(max = 300.dp)
                     ) {
                         DropdownMenuItem(
                             text = { Text("Ninguno (Borrar)", fontSize = 14.sp, color = Color(0xFFFF5252)) },
                             onClick = { onUpdateParcela(parcela.copy(verifiedUso = null)); showUsoDropdown = false }
                         )
                         allUsos.forEach { (code, desc) ->
                             DropdownMenuItem(
                                 text = { Text("$code - $desc", fontSize = 14.sp) },
                                 onClick = { onUpdateParcela(parcela.copy(verifiedUso = code)); showUsoDropdown = false }
                             )
                         }
                     }
                 }
             }

             Spacer(Modifier.height(12.dp))

             // ----------------------------
             // 2B. SELECTOR DE PRODUCTO
             // ----------------------------
             Text("CULTIVO/PRODUCTO OBSERVADO:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
             Spacer(Modifier.height(4.dp))

             Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(
                     imageVector = if(hasProdOverride) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                     contentDescription = null,
                     tint = if(hasProdOverride) Color(0xFF00FF88) else Color.Gray,
                     modifier = Modifier.size(24.dp).clickable {
                         if (hasProdOverride) onUpdateParcela(parcela.copy(verifiedProductoCode = null))
                         else showProductoDropdown = true
                     }
                 )
                 Spacer(Modifier.width(12.dp))
                 
                 Box(modifier = Modifier.weight(1f)) {
                     val buttonText = remember(parcela.verifiedProductoCode) {
                         if (parcela.verifiedProductoCode != null) {
                             val desc = SigpacCodeManager.getProductoDescription(parcela.verifiedProductoCode.toString())
                             "CULTIVO: $desc"
                         } else {
                             "SELECCIONAR CULTIVO..."
                         }
                     }

                     OutlinedButton(
                         onClick = { showProductoDropdown = true },
                         modifier = Modifier.fillMaxWidth(),
                         colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
                         border = BorderStroke(1.dp, if(hasProdOverride) Color(0xFF00FF88) else Color.Gray)
                     ) {
                         Text(
                             text = buttonText, 
                             fontSize = 13.sp,
                             maxLines = 1,
                             fontWeight = if(hasProdOverride) FontWeight.Bold else FontWeight.Normal
                         )
                     }
                     
                     DropdownMenu(
                         expanded = showProductoDropdown,
                         onDismissRequest = { showProductoDropdown = false },
                         modifier = Modifier.background(MaterialTheme.colorScheme.surface).heightIn(max = 300.dp)
                     ) {
                         DropdownMenuItem(
                             text = { Text("Ninguno (Borrar)", fontSize = 14.sp, color = Color(0xFFFF5252)) },
                             onClick = { onUpdateParcela(parcela.copy(verifiedProductoCode = null)); showProductoDropdown = false }
                         )
                         allProductos.forEach { (code, desc) ->
                             DropdownMenuItem(
                                 text = { Text("$code - $desc", fontSize = 14.sp) },
                                 onClick = { onUpdateParcela(parcela.copy(verifiedProductoCode = code)); showProductoDropdown = false }
                             )
                         }
                     }
                 }
             }

             // 3. Checks Contextuales (Pendiente, Croquis)
             val slope = parcela.sigpacInfo?.pendienteMedia ?: 0.0
             val usoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull()?.uppercase() ?: ""
             val isPastos = listOf("PS", "PR", "PA").contains(usoRaw)
             val showSlopeCheck = slope > 10.0 && (!isPastos || (isPastos && !agroAnalysis.isCompatible))

             if (showSlopeCheck) {
                 Spacer(Modifier.height(12.dp))
                 val slopeCheckId = "CHECK_PENDIENTE_10"
                 val isSlopeChecked = parcela.completedChecks.contains(slopeCheckId)
                 
                 InspectionCheckRow(
                     isChecked = isSlopeChecked,
                     title = "Pendiente compensada",
                     subtitle = "Se observan terrazas, bancales o laboreo a nivel.",
                     onToggle = {
                         val newChecks = if (isSlopeChecked) parcela.completedChecks - slopeCheckId else parcela.completedChecks + slopeCheckId
                         onUpdateParcela(parcela.copy(completedChecks = newChecks))
                     }
                 )
             }

             Spacer(Modifier.height(12.dp))
             val croquisCheckId = "CHECK_CROQUIS"
             val isCroquisChecked = parcela.completedChecks.contains(croquisCheckId)

             InspectionCheckRow(
                 isChecked = isCroquisChecked,
                 title = "Necesita croquis",
                 subtitle = "Se requiere adjuntar un croquis detallado de la superficie.",
                 onToggle = {
                     val newChecks = if (isCroquisChecked) parcela.completedChecks - croquisCheckId else parcela.completedChecks + croquisCheckId
                     onUpdateParcela(parcela.copy(completedChecks = newChecks))
                 }
             )
         }
    }

    // 4. Requisitos Específicos (Ecorregímenes, etc)
    Spacer(Modifier.height(12.dp))
    if (agroAnalysis.requirements.isNotEmpty()) {
        agroAnalysis.requirements.forEach { req ->
            val checkId = req.code ?: "REQ_${req.description.hashCode()}"
            val isChecked = parcela.completedChecks.contains(checkId)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if(isChecked) Color(0xFF00FF88).copy(0.05f) else Color.Black.copy(0.3f))
                    .border(1.dp, if(isChecked) Color(0xFF00FF88).copy(0.3f) else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                    .clickable {
                        val newChecks = if (isChecked) parcela.completedChecks - checkId else parcela.completedChecks + checkId
                        onUpdateParcela(parcela.copy(completedChecks = newChecks))
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                         imageVector = if(isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                         contentDescription = null,
                         tint = if(isChecked) Color(0xFF00FF88) else Color.Gray,
                         modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    
                    Column {
                        Text(req.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if(isChecked) Color(0xFF00FF88) else Color(0xFF62D2FF))
                        Spacer(Modifier.height(4.dp))
                        req.requirement.split("\n").forEach { line ->
                            if (line.isNotBlank()) {
                                Row(modifier = Modifier.padding(bottom = 3.dp), verticalAlignment = Alignment.Top) {
                                    Text("•", color = if(isChecked) Color(0xFF00FF88) else Color(0xFF62D2FF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                                    Text(line.trim(), fontSize = 14.sp, color = if(isChecked) Color.LightGray else Color.Gray, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Botones de Veredicto
    Spacer(Modifier.height(12.dp))
    Text("VEREDICTO FINAL", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    
    if (photosEnough) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VerdictButton(
                label = "CUMPLE",
                isSelected = parcela.finalVerdict == "CUMPLE",
                color = Color(0xFF00FF88),
                onClick = { 
                    val newVal = if (parcela.finalVerdict == "CUMPLE") null else "CUMPLE"
                    onUpdateParcela(parcela.copy(finalVerdict = newVal)) 
                }
            )

            VerdictButton(
                label = "NO CUMPLE",
                isSelected = parcela.finalVerdict == "NO_CUMPLE",
                color = Color(0xFFFF5252),
                onClick = { 
                    val newVal = if (parcela.finalVerdict == "NO_CUMPLE") null else "NO_CUMPLE"
                    onUpdateParcela(parcela.copy(finalVerdict = newVal)) 
                }
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.05f)).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Se requieren mínimo 2 fotos para finalizar", color = Color(0xFF62D2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InspectionCheckRow(isChecked: Boolean, title: String, subtitle: String, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.clickable { onToggle() }
    ) {
        Icon(
            imageVector = if(isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if(isChecked) Color(0xFF00FF88) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun RowScope.VerdictButton(label: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if(isSelected) color else Color.White.copy(0.05f),
            contentColor = if(isSelected) if (label == "CUMPLE") Color.Black else Color.White else Color.Gray
        ),
        border = if(isSelected) null else BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Text(label, fontWeight = FontWeight.Black)
    }
}
