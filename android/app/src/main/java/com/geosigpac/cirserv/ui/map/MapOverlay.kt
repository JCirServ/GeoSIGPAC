
package com.geosigpac.cirserv.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.ui.*
import java.util.Locale

// Enum para controlar el icono visual del botón
enum class MapLocationState {
    NONE,           // No centrado -> Icono: Mira (GpsNotFixed)
    TRACKING,       // Centrado Norte -> Icono: Brújula (Explore) -> Invita a modo orientación
    COMPASS         // Orientación -> Icono: Flecha (Navigation) -> Invita a resetear norte
}

@Composable
fun MapOverlay(
    searchQuery: String,
    isSearching: Boolean,
    isLoadingData: Boolean,
    currentBaseMap: BaseMap,
    showRecinto: Boolean,
    showCultivo: Boolean,
    isInfoSheetEnabled: Boolean,
    expedientes: List<NativeExpediente>,
    visibleProjectIds: Set<String>,
    instantSigpacRef: String,
    recintoData: Map<String, String>?,
    cultivoData: Map<String, String>?,
    mapCenterLat: Double? = null,
    mapCenterLng: Double? = null,
    userLat: Double? = null,
    userLng: Double? = null,
    userAccuracy: Float? = null,
    locationState: MapLocationState = MapLocationState.NONE, // Estado visual del botón
    // Actions
    onSearchQueryChange: (String) -> Unit,
    onSearchPerform: () -> Unit,
    onClearSearch: () -> Unit,
    onChangeBaseMap: (BaseMap) -> Unit,
    onToggleRecinto: (Boolean) -> Unit,
    onToggleCultivo: (Boolean) -> Unit,
    onToggleInfoSheet: (Boolean) -> Unit,
    onToggleProjectVisibility: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    onOpenCamera: () -> Unit,
    onCenterLocation: () -> Unit
) {
    val context = LocalContext.current
    var showCustomKeyboard by remember { mutableStateOf(false) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showProjectsMenu by remember { mutableStateOf(false) }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // --- SEARCH BAR & CENTER POINTER ---
    if (!isSearching && !showCustomKeyboard) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = Color.Black.copy(0.5f), modifier = Modifier.size(38.dp))
            Icon(Icons.Default.Add, "Puntero", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }

    // --- CAJETÍN DE BÚSQUEDA Y COORDENADAS ---
    Box(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp).fillMaxWidth(0.65f)) {
        Column {
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showCustomKeyboard = true
                        isPanelExpanded = false
                    }
                }
            }
            TextField(
                value = searchQuery,
                onValueChange = { },
                placeholder = { Text("Prov:Mun:Pol:Parc", color = Color.Gray, fontSize = 16.sp) },
                singleLine = true, maxLines = 1, readOnly = true, interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HighContrastWhite, fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = FieldBackground.copy(alpha = 0.9f),
                    unfocusedContainerColor = FieldBackground.copy(alpha = 0.7f),
                    focusedIndicatorColor = if(showCustomKeyboard) FieldGreen else Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = FieldGreen
                ),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onClearSearch(); showCustomKeyboard = false }) {
                            Icon(Icons.Default.Close, "Borrar", tint = Color.Gray)
                        }
                    } else {
                        IconButton(onClick = { showCustomKeyboard = true }) {
                            Icon(Icons.Default.Search, "Buscar", tint = FieldGreen)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            
            // COORDENADAS DEL USUARIO (GPS) DEBAJO DEL BUSCADOR
            if (userLat != null && userLng != null && userLat != 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    val accuracyText = if (userAccuracy != null) " (±${userAccuracy.toInt()}m)" else ""
                    Text(
                        text = String.format(Locale.US, "GPS: %.6f, %.6f%s", userLat, userLng, accuracyText),
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // --- FLOATING ACTION BUTTONS ---
    Column(
        modifier = Modifier.padding(top = 90.dp, end = 16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(onClick = { showLayerMenu = !showLayerMenu; showProjectsMenu = false }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.Settings, "Capas") }
            
            AnimatedVisibility(visible = showLayerMenu) {
                Card(modifier = Modifier.width(220.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Mapa Base", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        BaseMap.values().forEach { base -> Row(modifier = Modifier.fillMaxWidth().clickable { onChangeBaseMap(base) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = (currentBaseMap == base), onClick = { onChangeBaseMap(base) }, modifier = Modifier.size(24.dp), colors = RadioButtonDefaults.colors(selectedColor = FieldGreen)); Spacer(modifier = Modifier.width(10.dp)); Text(base.title, fontSize = 16.sp) } }
                        
                        Divider(modifier = Modifier.padding(vertical = 10.dp))
                        
                        Text("Capas SIGPAC", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onToggleRecinto(!showRecinto) }) { Checkbox(checked = showRecinto, onCheckedChange = { onToggleRecinto(it) }, modifier = Modifier.size(36.dp).padding(4.dp), colors = CheckboxDefaults.colors(checkedColor = FieldGreen)); Text("Recintos", fontSize = 16.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onToggleCultivo(!showCultivo) }) { Checkbox(checked = showCultivo, onCheckedChange = { onToggleCultivo(it) }, modifier = Modifier.size(36.dp).padding(4.dp), colors = CheckboxDefaults.colors(checkedColor = FieldGreen)); Text("Cultivos", fontSize = 16.sp) }
                        
                        Divider(modifier = Modifier.padding(vertical = 10.dp))
                        
                        Text("Interfaz", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onToggleInfoSheet(!isInfoSheetEnabled) }) { 
                            Checkbox(
                                checked = isInfoSheetEnabled, 
                                onCheckedChange = { onToggleInfoSheet(it) }, 
                                modifier = Modifier.size(36.dp).padding(4.dp), 
                                colors = CheckboxDefaults.colors(checkedColor = FieldGreen)
                            )
                            Text("Ficha Info", fontSize = 16.sp) 
                        }
                    }
                }
            }

            SmallFloatingActionButton(onClick = { showProjectsMenu = !showProjectsMenu; showLayerMenu = false }, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Text("KML", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            
            AnimatedVisibility(visible = showProjectsMenu) {
                Card(modifier = Modifier.width(220.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Proyectos (KML)", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (expedientes.isEmpty()) { Text("No hay proyectos cargados", fontSize = 15.sp, color = Color.Gray, modifier = Modifier.padding(4.dp)) } else {
                            Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) { expedientes.forEach { exp -> val isVisible = visibleProjectIds.contains(exp.id); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onToggleProjectVisibility(exp.id) }) { Checkbox(checked = isVisible, onCheckedChange = { onToggleProjectVisibility(exp.id) }, modifier = Modifier.size(36.dp).padding(4.dp), colors = CheckboxDefaults.colors(checkedColor = FieldGreen)); Text(exp.titular, fontSize = 15.sp, maxLines = 1) } } }
                        }
                    }
                }
            }

            SmallFloatingActionButton(onClick = onNavigateToProjects, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.List, "Proyectos") }
            SmallFloatingActionButton(onClick = onOpenCamera, containerColor = MaterialTheme.colorScheme.surface, contentColor = FieldGreen, shape = CircleShape) { Icon(Icons.Default.CameraAlt, "Cámara") }
            
            // BOTÓN UBICACIÓN / ORIENTACIÓN
            // Iconos nativos del tema para indicar el estado de navegación
            SmallFloatingActionButton(
                onClick = onCenterLocation, 
                containerColor = MaterialTheme.colorScheme.surface, 
                contentColor = FieldGreen, // Color Neon Green del Tema
                shape = CircleShape
            ) { 
                val icon = when(locationState) {
                    MapLocationState.NONE -> Icons.Default.GpsNotFixed // Mira abierta
                    MapLocationState.TRACKING -> Icons.Default.Explore // Brújula (Indica "Norte Fijo")
                    MapLocationState.COMPASS -> Icons.Default.Navigation // Flecha navegación (Indica "Orientado")
                }
                Icon(icon, "Ubicación") 
            }
        }
    }

    // --- LOADING INDICATOR ---
    if (isLoadingData || isSearching) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
            CircularProgressIndicator(color = FieldGreen, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
        }
    }

    // --- INFO SHEET ---
    if (!showCustomKeyboard) {
        // La ficha solo se muestra si el toggle está activado Y hay datos relevantes
        val showSheet = isInfoSheetEnabled && (instantSigpacRef.isNotEmpty() || recintoData != null || (cultivoData != null && showCultivo))
        
        AnimatedVisibility(
            visible = showSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                val displayData = recintoData ?: mapOf("provincia" to "", "municipio" to "")
                val currentRef = if (instantSigpacRef.isNotEmpty()) instantSigpacRef else "${displayData["provincia"]}:${displayData["municipio"]}..."
                
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        .pointerInput(Unit) { detectVerticalDragGestures { change, dragAmount -> change.consume(); if (dragAmount < -20) isPanelExpanded = true else if (dragAmount > 20) isPanelExpanded = false } },
                    colors = CardDefaults.cardColors(containerColor = FieldBackground.copy(alpha = 0.98f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color.White.copy(alpha = 0.3f))) }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) { 
                                Text("REF. SIGPAC", style = MaterialTheme.typography.labelSmall, color = FieldGray, fontSize = 13.sp)
                                Text(text = currentRef, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = FieldGreen, fontSize = 20.sp) 
                            }
                            
                            // BOTONES DE ACCIÓN (COPIAR / MAPS / CERRAR)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 1. Copiar al Portapapeles
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SIGPAC", currentRef)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Referencia copiada", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Copiar", tint = FieldGreen, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))

                                // 2. Abrir Google Maps (Usa el centro del mapa/retículo para la ubicación de destino)
                                if (mapCenterLat != null && mapCenterLng != null && mapCenterLat != 0.0) {
                                    IconButton(onClick = {
                                        try {
                                            // Geo URI para navegación
                                            val uri = Uri.parse("geo:0,0?q=$mapCenterLat,$mapCenterLng(Parcela $currentRef)")
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se puede abrir el mapa", Toast.LENGTH_SHORT).show()
                                        }
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Map, "Google Maps", tint = Color(0xFF62D2FF), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                // 3. Cerrar Ficha
                                IconButton(onClick = { onClearSearch(); isPanelExpanded = false }, modifier = Modifier.size(32.dp)) { 
                                    Icon(Icons.Default.Close, "Cerrar", tint = HighContrastWhite, modifier = Modifier.size(24.dp)) 
                                }
                            }
                        }
                        
                        if (recintoData != null) {
                            Divider(color = FieldDivider)
                            val hasCultivo = cultivoData != null
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50)).background(FieldSurface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedTab == 0) FieldGreen else Color.Transparent).clickable { selectedTab = 0; isPanelExpanded = true }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Recinto", fontWeight = FontWeight.Bold, color = if(selectedTab == 0) Color.White else FieldGray, fontSize = 15.sp) }
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selectedTab == 1) FieldGreen else Color.Transparent).clickable(enabled = hasCultivo) { if (hasCultivo) { selectedTab = 1; isPanelExpanded = true } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Cultivo", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else if (hasCultivo) FieldGray else Color.White.copy(alpha = 0.2f), fontSize = 15.sp) }
                            }
                            if (isPanelExpanded) {
                                Divider(color = FieldDivider)
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    if (selectedTab == 0) {
                                        if (recintoData != null) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Uso SIGPAC", recintoData["uso_sigpac"], Modifier.weight(1f)); AttributeItem("Superficie", "${recintoData["superficie"]} ha", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Pendiente Media", "${recintoData["pendiente_media"]}%", Modifier.weight(1f)); AttributeItem("Altitud", "${recintoData["altitud"]} m", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Región", recintoData["region"], Modifier.weight(1f)); AttributeItem("Coef. Regadío", "${recintoData["coef_regadio"]}%", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AttributeItem("Subvencionabilidad", "${recintoData["subvencionabilidad"]}%", Modifier.weight(1f)); Column(modifier = Modifier.weight(1f)) { val incidencias = recintoData["incidencias"]; if (!incidencias.isNullOrEmpty()) { IncidenciaMapItem(incidencias) } else { AttributeItem("Incidencias", "Ninguna", Modifier) } } }
                                        }
                                    } else {
                                        if (cultivoData != null) {
                                            val c = cultivoData
                                            Text("Datos de Expediente", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=6.dp), fontSize = 14.sp)
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Núm. Exp", c["exp_num"], Modifier.weight(1f)); AttributeItem("Año", c["exp_ano"], Modifier.weight(1f)) }
                                            Divider(Modifier.padding(vertical=8.dp), color = FieldDivider)
                                            Text("Datos Agrícolas", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=6.dp), fontSize = 14.sp)
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Producto", c["parc_producto"], Modifier.weight(1f)); val supCultRaw = c["parc_supcult"]?.toDoubleOrNull() ?: 0.0; val supCultHa = supCultRaw / 10000.0; AttributeItem("Superficie", "${String.format(Locale.US, "%.4f", supCultHa)} ha", Modifier.weight(1f)) }
                                            Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth()) { val sist = c["parc_sistexp"]; val sistLabel = when(sist) { "S" -> "Secano"; "R" -> "Regadío"; else -> sist }; AttributeItem("Sist. Expl.", sistLabel, Modifier.weight(1f)); AttributeItem("Ind. Cultivo", c["parc_indcultapro"], Modifier.weight(1f)) }
                                            Spacer(Modifier.height(12.dp)); AttributeItem("Tipo Aprovechamiento", c["tipo_aprovecha"], Modifier.fillMaxWidth())
                                            Divider(Modifier.padding(vertical=8.dp), color = FieldDivider)
                                            Text("Ayudas Solicitadas", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=6.dp), fontSize = 14.sp)
                                            if (!c["parc_ayudasol"].isNullOrEmpty()) { IncidenciaMapItem(c["parc_ayudasol"]!!, type = "AYUDA") } else { AttributeItem("Ayudas Parc.", "Ninguna", Modifier.fillMaxWidth()) }
                                            Spacer(Modifier.height(6.dp)); if (!c["pdr_rec"].isNullOrEmpty()) { Text("Ayudas PDR", style = MaterialTheme.typography.labelSmall, color = FieldGray, fontSize = 14.sp); IncidenciaMapItem(c["pdr_rec"]!!, type = "PDR") } else { AttributeItem("Ayudas PDR", c["pdr_rec"], Modifier.fillMaxWidth()) }
                                            Divider(Modifier.padding(vertical=8.dp), color = FieldDivider)
                                            Text("Cultivo Secundario", style = MaterialTheme.typography.labelMedium, color = FieldGreen, modifier = Modifier.padding(vertical=6.dp), fontSize = 14.sp)
                                            Row(Modifier.fillMaxWidth()) { AttributeItem("Producto Sec.", c["cultsecun_producto"], Modifier.weight(1f)); Column(modifier = Modifier.weight(1f)) { if (!c["cultsecun_ayudasol"].isNullOrEmpty()) { IncidenciaMapItem(c["cultsecun_ayudasol"]!!, type = "AYUDA") } else { AttributeItem("Ayuda Sec.", "Ninguna", Modifier) } } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    

    // --- KEYBOARD ---
    AnimatedVisibility(
        visible = showCustomKeyboard,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CustomSigpacKeyboard(
                onKey = { char -> onSearchQueryChange(searchQuery + char) },
                onBackspace = { if (searchQuery.isNotEmpty()) onSearchQueryChange(searchQuery.dropLast(1)) },
                onSearch = { 
                    onSearchPerform()
                    showCustomKeyboard = false // Oculta el teclado tras pulsar Buscar
                },
                onClose = { showCustomKeyboard = false }
            )
        }
    }
}
