
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    // Usamos remember con key para que, si el componente se "reinicia" con initiallyExpanded=true, lo respete
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded || parcela.photos.isNotEmpty()) } 
    // Secciones internas colapsadas por defecto, salvo si se indica lo contrario
    var inspectionExpanded by remember { mutableStateOf(true) } 
    var dataExpanded by remember { mutableStateOf(initiallyTechExpanded) }
    
    // GALERÍA DE FOTOS
    var showGallery by remember { mutableStateOf(false) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val isLoading = !parcela.isHydrated
    
    // Lista de usos comunes para el desplegable
    val commonUsos = remember { SigpacCodeManager.getCommonUsos() }
    var showUsoDropdown by remember { mutableStateOf(false) }

    // Cálculo de Análisis Agronómico
    val agroAnalysis = remember(parcela.sigpacInfo, parcela.cultivoInfo, parcela.isHydrated) {
        if (parcela.isHydrated) {
            val prodDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.parcProducto?.toString())
            SigpacCodeManager.performAgroAnalysis(
                productoCode = parcela.cultivoInfo?.parcProducto,
                productoDesc = prodDesc,
                sigpacUso = parcela.sigpacInfo?.usoSigpac,
                ayudasRaw = parcela.cultivoInfo?.parcAyudasol,
                pdrRaw = parcela.cultivoInfo?.pdrRec,
                sistExp = parcela.cultivoInfo?.parcSistexp,
                coefRegadio = parcela.sigpacInfo?.coefRegadio,
                pendienteMedia = parcela.sigpacInfo?.pendienteMedia
            )
        } else null
    }
    
    LaunchedEffect(agroAnalysis) {
        if (agroAnalysis != null && parcela.verifiedUso == null) {
             val sigpacUsoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull() 
             if (agroAnalysis.isCompatible && sigpacUsoRaw != null) {
                 onUpdateParcela(parcela.copy(verifiedUso = sigpacUsoRaw))
             }
        }
    }

    val photosEnough = parcela.photos.size >= 2
    val isFullyCompleted = remember(parcela.finalVerdict, photosEnough) {
        parcela.finalVerdict != null && photosEnough
    }

    val statusColor = remember(agroAnalysis, isLoading, isFullyCompleted, photosEnough) {
        when {
            isLoading -> Color.Gray
            !photosEnough -> Color(0xFF62D2FF)
            isFullyCompleted -> Color(0xFF62D2FF)
            agroAnalysis == null -> Color.Gray
            !agroAnalysis.isCompatible -> Color(0xFFFF5252)
            agroAnalysis.explanation.contains("AVISO", ignoreCase = true) || agroAnalysis.explanation.contains("Nota", ignoreCase = true) -> Color(0xFFFF9800)
            else -> Color(0xFF00FF88)
        }
    }

    val statusIcon = remember(agroAnalysis, isLoading, isFullyCompleted, photosEnough) {
         when {
            isLoading -> null
            !photosEnough -> Icons.Default.CameraAlt
            isFullyCompleted -> Icons.Default.Verified
            agroAnalysis == null -> null
            !agroAnalysis.isCompatible -> Icons.Default.Error
            agroAnalysis.explanation.contains("AVISO", ignoreCase = true) -> Icons.Default.Warning
            else -> null
        }
    }

    val cardBackgroundColor = if (isFullyCompleted) {
        Color(0xFF00FF88).copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    
    // --- COMPONENTE GALERÍA ---
    if (showGallery) {
        FullScreenPhotoGallery(
            photos = parcela.photos,
            initialIndex = galleryInitialIndex,
            onDismiss = { showGallery = false },
            onDeletePhoto = { photoUriToDelete ->
                val updatedPhotos = parcela.photos.filter { it != photoUriToDelete }
                onUpdateParcela(parcela.copy(photos = updatedPhotos))
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if(isFullyCompleted) 2.dp else 1.dp, statusColor.copy(alpha = if(parcela.isHydrated) 0.8f else 0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
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
                    IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(24.dp)) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Yellow)
                }
            }

            if (expanded) {
                if (parcela.isHydrated && agroAnalysis != null) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        
                        // --- EVIDENCIA FOTOGRÁFICA ---
                        CollapsibleHeader("EVIDENCIA FOTOGRÁFICA (${parcela.photos.size}/2)", true) { }
                        
                        if (parcela.photos.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(parcela.photos) { index, uriStr ->
                                    AsyncImage(
                                        model = Uri.parse(uriStr),
                                        contentDescription = "Foto evidencia",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp))
                                            .clickable { 
                                                galleryInitialIndex = index
                                                showGallery = true
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(60.dp).background(Color.White.copy(0.05f), RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp)).clickable { onCamera(parcela.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Añadir fotos requeridas", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // --- INSPECCIÓN VISUAL ---
                        CollapsibleHeader("INSPECCIÓN VISUAL & REQUISITOS", inspectionExpanded) { inspectionExpanded = !inspectionExpanded }
                        
                        if (inspectionExpanded) {
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
                                     val hasManualOverride = parcela.verifiedUso != null
                                     
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

                                     if (isUseIncompatible || isWarning || hasManualOverride) {
                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             val isUsoChecked = parcela.verifiedUso != null
                                             Icon(
                                                 imageVector = if(isUsoChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                 contentDescription = null,
                                                 tint = if(isUsoChecked) Color(0xFF00FF88) else Color.Gray,
                                                 modifier = Modifier.size(24.dp).clickable {
                                                     if (isUsoChecked) {
                                                         onUpdateParcela(parcela.copy(verifiedUso = null))
                                                     } else {
                                                         showUsoDropdown = true
                                                     }
                                                 }
                                             )
                                             Spacer(Modifier.width(12.dp))
                                             
                                             Box(modifier = Modifier.weight(1f)) {
                                                 OutlinedButton(
                                                     onClick = { showUsoDropdown = true },
                                                     modifier = Modifier.fillMaxWidth(),
                                                     colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
                                                     border = BorderStroke(1.dp, if(isUsoChecked) Color(0xFF00FF88) else Color.Gray)
                                                 ) {
                                                     Text(text = if(parcela.verifiedUso != null) "USO OBSERVADO: ${parcela.verifiedUso}" else "SELECCIONAR USO REAL...", fontSize = 13.sp)
                                                 }
                                                 
                                                 DropdownMenu(
                                                     expanded = showUsoDropdown,
                                                     onDismissRequest = { showUsoDropdown = false },
                                                     modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                                 ) {
                                                     DropdownMenuItem(
                                                         text = { Text("Ninguno (Borrar)", fontSize = 14.sp, color = Color(0xFFFF5252)) },
                                                         onClick = { 
                                                             onUpdateParcela(parcela.copy(verifiedUso = null))
                                                             showUsoDropdown = false 
                                                         }
                                                     )
                                                     commonUsos.forEach { (code, desc) ->
                                                         DropdownMenuItem(
                                                             text = { Text("$code - $desc", fontSize = 14.sp) },
                                                             onClick = { 
                                                                 onUpdateParcela(parcela.copy(verifiedUso = code))
                                                                 showUsoDropdown = false 
                                                             }
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }

                                     val slope = parcela.sigpacInfo?.pendienteMedia ?: 0.0
                                     val usoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull()?.uppercase() ?: ""
                                     val isPastos = listOf("PS", "PR", "PA").contains(usoRaw)
                                     
                                     val showSlopeCheck = slope > 10.0 && (!isPastos || (isPastos && !agroAnalysis.isCompatible))

                                     if (showSlopeCheck) {
                                         Spacer(Modifier.height(12.dp))
                                         val slopeCheckId = "CHECK_PENDIENTE_10"
                                         val isSlopeChecked = parcela.completedChecks.contains(slopeCheckId)
                                         
                                         Row(
                                             verticalAlignment = Alignment.Top,
                                             modifier = Modifier.clickable {
                                                 val newChecks = if (isSlopeChecked) parcela.completedChecks - slopeCheckId else parcela.completedChecks + slopeCheckId
                                                 onUpdateParcela(parcela.copy(completedChecks = newChecks))
                                             }
                                         ) {
                                             Icon(
                                                 imageVector = if(isSlopeChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                 contentDescription = null,
                                                 tint = if(isSlopeChecked) Color(0xFF00FF88) else Color.Gray,
                                                 modifier = Modifier.size(24.dp)
                                             )
                                             Spacer(Modifier.width(12.dp))
                                             Column {
                                                 Text("Pendiente compensada", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                 Text("Se observan terrazas, bancales o laboreo a nivel.", color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
                                             }
                                         }
                                     }

                                     Spacer(Modifier.height(12.dp))
                                     val croquisCheckId = "CHECK_CROQUIS"
                                     val isCroquisChecked = parcela.completedChecks.contains(croquisCheckId)

                                     Row(
                                         verticalAlignment = Alignment.Top,
                                         modifier = Modifier.clickable {
                                             val newChecks = if (isCroquisChecked) parcela.completedChecks - croquisCheckId else parcela.completedChecks + croquisCheckId
                                             onUpdateParcela(parcela.copy(completedChecks = newChecks))
                                         }
                                     ) {
                                         Icon(
                                             imageVector = if(isCroquisChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                             contentDescription = null,
                                             tint = if(isCroquisChecked) Color(0xFF00FF88) else Color.Gray,
                                             modifier = Modifier.size(24.dp)
                                         )
                                         Spacer(Modifier.width(12.dp))
                                         Column {
                                             Text("Necesita croquis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                             Text("Se requiere adjuntar un croquis detallado de la superficie.", color = Color.Gray, fontSize = 12.sp, lineHeight = 16.sp)
                                         }
                                     }
                                 }
                            }

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

                            Spacer(Modifier.height(12.dp))
                            Text("VEREDICTO FINAL", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                            
                            if (photosEnough) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { 
                                            val newVal = if (parcela.finalVerdict == "CUMPLE") null else "CUMPLE"
                                            onUpdateParcela(parcela.copy(finalVerdict = newVal)) 
                                        },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if(parcela.finalVerdict == "CUMPLE") Color(0xFF00FF88) else Color.White.copy(0.05f),
                                            contentColor = if(parcela.finalVerdict == "CUMPLE") Color.Black else Color.Gray
                                        ),
                                        border = if(parcela.finalVerdict == "CUMPLE") null else BorderStroke(1.dp, Color.White.copy(0.1f))
                                    ) {
                                        Text("CUMPLE", fontWeight = FontWeight.Black)
                                    }

                                    Button(
                                        onClick = { 
                                            val newVal = if (parcela.finalVerdict == "NO_CUMPLE") null else "NO_CUMPLE"
                                            onUpdateParcela(parcela.copy(finalVerdict = newVal)) 
                                        },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if(parcela.finalVerdict == "NO_CUMPLE") Color(0xFFFF5252) else Color.White.copy(0.05f),
                                            contentColor = if(parcela.finalVerdict == "NO_CUMPLE") Color.White else Color.Gray
                                        ),
                                        border = if(parcela.finalVerdict == "NO_CUMPLE") null else BorderStroke(1.dp, Color.White.copy(0.1f))
                                    ) {
                                        Text("NO CUMPLE", fontWeight = FontWeight.Black)
                                    }
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

                        Spacer(Modifier.height(20.dp))
                        Divider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(20.dp))

                        CollapsibleHeader("DATOS TÉCNICOS & GEOMÉTRICOS", dataExpanded) { dataExpanded = !dataExpanded }

                        if (dataExpanded) {
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
                        }
                        
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { 
                                val parts = parcela.referencia.split("[:\\-]".toRegex()).filter { it.isNotEmpty() }
                                val searchStr = if (parts.size >= 7) {
                                    "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
                                } else {
                                    parts.joinToString(":")
                                }
                                onLocate(searchStr)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("LOCALIZAR EN MAPA", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
