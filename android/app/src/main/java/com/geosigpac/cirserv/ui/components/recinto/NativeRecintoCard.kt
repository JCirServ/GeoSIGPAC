
package com.geosigpac.cirserv.ui.components.recinto

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.utils.AnalysisSeverity
import com.geosigpac.cirserv.utils.SigpacCodeManager
import com.geosigpac.cirserv.ui.FullScreenPhotoGallery

@Composable
fun NativeRecintoCard(
    parcela: NativeParcela, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateParcela: (NativeParcela) -> Unit,
    initiallyExpanded: Boolean = false,
    initiallyTechExpanded: Boolean = false
) {
    // Estado Expansión
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded || parcela.photos.isNotEmpty()) } 
    var inspectionExpanded by remember { mutableStateOf(true) } 
    var dataExpanded by remember { mutableStateOf(initiallyTechExpanded) }
    
    // Galería
    var showGallery by remember { mutableStateOf(false) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    
    val isLoading = !parcela.isHydrated
    val photosEnough = parcela.photos.size >= 2
    val isFullyCompleted = parcela.finalVerdict != null && photosEnough

    // --- CÁLCULO DE ANÁLISIS ---
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

    // Auto-corrección de uso y producto si es totalmente compatible
    LaunchedEffect(agroAnalysis) {
        if (agroAnalysis != null) {
             val sigpacUsoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull() 
             val declaredProd = parcela.cultivoInfo?.parcProducto

             // Solo si es compatible
             if (agroAnalysis.isCompatible) {
                 var newParcela = parcela
                 var changed = false

                 // 1. Auto-verificar Uso
                 if (parcela.verifiedUso == null && sigpacUsoRaw != null) {
                     newParcela = newParcela.copy(verifiedUso = sigpacUsoRaw)
                     changed = true
                 }
                 // 2. Auto-verificar Producto (NUEVO)
                 if (parcela.verifiedProductoCode == null && declaredProd != null) {
                     newParcela = newParcela.copy(verifiedProductoCode = declaredProd)
                     changed = true
                 }

                 if (changed) {
                     onUpdateParcela(newParcela)
                 }
             }
        }
    }

    // --- ESTILOS DINÁMICOS BASADOS EN SEVERIDAD ---
    val statusColor = remember(agroAnalysis, isLoading, isFullyCompleted, photosEnough) {
        when {
            isLoading -> Color.Gray
            !photosEnough -> Color(0xFF62D2FF)
            isFullyCompleted -> Color(0xFF62D2FF)
            agroAnalysis == null -> Color.Gray
            // Usamos la nueva Enum de Severidad
            agroAnalysis.severity == AnalysisSeverity.CRITICAL -> Color(0xFFFF5252) // ROJO
            agroAnalysis.severity == AnalysisSeverity.WARNING -> Color(0xFFFF9800) // NARANJA
            else -> Color(0xFF00FF88) // VERDE
        }
    }

    val statusIcon = remember(agroAnalysis, isLoading, isFullyCompleted, photosEnough) {
         when {
            isLoading -> null
            !photosEnough -> Icons.Default.CameraAlt
            isFullyCompleted -> Icons.Default.Verified
            agroAnalysis == null -> null
            agroAnalysis.severity == AnalysisSeverity.CRITICAL -> Icons.Default.Error
            agroAnalysis.severity == AnalysisSeverity.WARNING -> Icons.Default.Warning
            else -> null
        }
    }

    // Capturamos el color surface fuera de remember porque MaterialTheme.colorScheme es @Composable
    val surfaceColor = MaterialTheme.colorScheme.surface

    // El fondo de la tarjeta ahora refleja la severidad del problema
    val cardBackgroundColor = remember(isFullyCompleted, agroAnalysis, isLoading, surfaceColor) {
        if (isFullyCompleted) {
            Color(0xFF00FF88).copy(alpha = 0.15f)
        } else if (!isLoading && agroAnalysis != null) {
            when (agroAnalysis.severity) {
                AnalysisSeverity.CRITICAL -> Color(0xFFFF5252).copy(alpha = 0.15f)
                AnalysisSeverity.WARNING -> Color(0xFFFF9800).copy(alpha = 0.15f)
                else -> surfaceColor.copy(alpha = 0.5f)
            }
        } else {
            surfaceColor.copy(alpha = 0.5f)
        }
    }

    // --- GALERÍA FULLSCREEN ---
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

    // --- CARD CONTAINER ---
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if(isFullyCompleted || agroAnalysis?.severity == AnalysisSeverity.CRITICAL) 2.dp else 1.dp, statusColor.copy(alpha = if(parcela.isHydrated) 0.8f else 0.1f))
    ) {
        Column {
            // 1. Header
            RecintoHeader(
                parcela = parcela,
                isLoading = isLoading,
                isFullyCompleted = isFullyCompleted,
                photosEnough = photosEnough,
                agroAnalysis = agroAnalysis,
                statusColor = statusColor,
                statusIcon = statusIcon,
                onExpand = { expanded = !expanded },
                onCamera = { onCamera(parcela.id) }
            )

            // 2. Contenido Expandido
            if (expanded) {
                if (parcela.isHydrated && agroAnalysis != null) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        
                        // A. Fotos
                        CollapsibleHeader("EVIDENCIA FOTOGRÁFICA (${parcela.photos.size}/2)", true) { }
                        RecintoPhotos(
                            photos = parcela.photos,
                            onCamera = { onCamera(parcela.id) },
                            onGalleryOpen = { idx -> 
                                galleryInitialIndex = idx
                                showGallery = true
                            }
                        )

                        // B. Inspección Visual
                        CollapsibleHeader("INSPECCIÓN VISUAL & REQUISITOS", inspectionExpanded) { inspectionExpanded = !inspectionExpanded }
                        if (inspectionExpanded) {
                            RecintoInspection(
                                parcela = parcela,
                                agroAnalysis = agroAnalysis,
                                photosEnough = photosEnough,
                                onUpdateParcela = onUpdateParcela
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Divider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(20.dp))

                        // C. Datos Técnicos
                        CollapsibleHeader("DATOS TÉCNICOS & GEOMÉTRICOS", dataExpanded) { dataExpanded = !dataExpanded }
                        if (dataExpanded) {
                            RecintoTechnicalData(
                                parcela = parcela
                            )
                        }

                        // D. Botón Localizar (Siempre Visible al desplegar)
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
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
