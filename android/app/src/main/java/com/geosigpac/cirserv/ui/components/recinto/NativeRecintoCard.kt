
package com.geosigpac.cirserv.ui.components.recinto

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.services.GeminiService
import com.geosigpac.cirserv.utils.AnalysisSeverity
import com.geosigpac.cirserv.utils.SigpacCodeManager
import com.geosigpac.cirserv.ui.FullScreenPhotoGallery
import kotlinx.coroutines.launch

@Composable
fun NativeRecintoCard(
    parcela: NativeParcela, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateParcela: (NativeParcela) -> Unit,
    initiallyExpanded: Boolean = false,
    initiallyTechExpanded: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded || parcela.photos.isNotEmpty()) } 
    var inspectionExpanded by remember { mutableStateOf(true) } 
    var dataExpanded by remember { mutableStateOf(initiallyTechExpanded) }
    
    var showGallery by remember { mutableStateOf(false) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    
    // IA Vision State
    var isAnalyzingPhoto by remember { mutableStateOf(false) }
    var visionReport by remember { mutableStateOf<String?>(null) }

    val isLoading = !parcela.isHydrated
    val photosEnough = parcela.photos.size >= 2
    val isFullyCompleted = parcela.finalVerdict != null && photosEnough

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

    val statusColor = remember(agroAnalysis, isLoading, isFullyCompleted, photosEnough) {
        when {
            isLoading -> Color.Gray
            isFullyCompleted -> Color(0xFF00FF88)
            agroAnalysis?.severity == AnalysisSeverity.CRITICAL -> Color(0xFFFF5252)
            agroAnalysis?.severity == AnalysisSeverity.WARNING -> Color(0xFFFF9800)
            !photosEnough -> Color(0xFF62D2FF)
            else -> Color(0xFF00FF88)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column {
            RecintoHeader(
                parcela = parcela,
                isLoading = isLoading,
                isFullyCompleted = isFullyCompleted,
                photosEnough = photosEnough,
                agroAnalysis = agroAnalysis,
                statusColor = statusColor,
                statusIcon = if(isFullyCompleted) Icons.Default.Verified else null,
                onExpand = { expanded = !expanded },
                onCamera = { onCamera(parcela.id) }
            )

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    
                    // --- SECCIÓN DE EVIDENCIA CON IA ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        CollapsibleHeader("FOTOS DE CAMPO (${parcela.photos.size})", true) { }
                        if (parcela.photos.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isAnalyzingPhoto = true
                                        val lastPhoto = Uri.parse(parcela.photos.last())
                                        val prodDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.parcProducto?.toString()) ?: "Desconocido"
                                        visionReport = GeminiService.analyzePhotoConsistency(
                                            context, lastPhoto, prodDesc, parcela.sigpacInfo?.usoSigpac ?: "N/D"
                                        )
                                        isAnalyzingPhoto = false
                                    }
                                },
                                enabled = !isAnalyzingPhoto
                            ) {
                                if (isAnalyzingPhoto) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ANALIZAR FOTO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (visionReport != null) {
                        Surface(
                            modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                            color = Color(0xFF62D2FF).copy(0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF62D2FF).copy(0.3f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, null, tint = Color(0xFF62D2FF), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(visionReport!!, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    RecintoPhotos(
                        photos = parcela.photos,
                        onCamera = { onCamera(parcela.id) },
                        onGalleryOpen = { idx -> 
                            galleryInitialIndex = idx
                            showGallery = true
                        }
                    )

                    // ... Inspección Visual y Datos Técnicos (Resto igual)
                }
            }
        }
    }
    
    if (showGallery) {
        FullScreenPhotoGallery(
            photos = parcela.photos,
            initialIndex = galleryInitialIndex,
            onDismiss = { showGallery = false },
            onDeletePhoto = { uri ->
                onUpdateParcela(parcela.copy(photos = parcela.photos.filter { it != uri }))
            }
        )
    }
}
