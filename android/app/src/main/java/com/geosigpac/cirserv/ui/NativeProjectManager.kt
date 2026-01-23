
package com.geosigpac.cirserv.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.services.GeminiService
import com.geosigpac.cirserv.services.SigpacApiService
import com.geosigpac.cirserv.utils.KmlParser
import com.geosigpac.cirserv.utils.SigpacCodeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeProjectManager(
    expedientes: List<NativeExpediente>,
    activeProjectId: String?,
    onUpdateExpedientes: (List<NativeExpediente>) -> Unit,
    onActivateProject: (String) -> Unit,
    onNavigateToMap: (String) -> Unit,
    onOpenCamera: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedExpedienteId by remember { mutableStateOf<String?>(null) }
    val currentExpedientesState = rememberUpdatedState(expedientes)
    
    var activeHydrations by remember { mutableStateOf<Set<String>>(setOf()) }

    fun startHydrationSequence(targetExpId: String) {
        if (activeHydrations.contains(targetExpId)) return
        activeHydrations = activeHydrations + targetExpId
        
        scope.launch {
            try {
                while (true) {
                    val currentList = currentExpedientesState.value
                    val exp = currentList.find { it.id == targetExpId } ?: break
                    val parcelaToHydrate = exp.parcelas.find { !it.isHydrated } 
                    
                    if (parcelaToHydrate == null) break
                    
                    var updatedParcela = parcelaToHydrate.copy(isHydrated = true)

                    // CASO ESPECIAL: Parcela importada como PUNTO (sin geometría, ref pendiente)
                    if (parcelaToHydrate.geometryRaw == null && parcelaToHydrate.centroidLat != null) {
                        Log.d("SigpacDebug", "Hydrating Point Marker: ${parcelaToHydrate.centroidLat}, ${parcelaToHydrate.centroidLng}")
                        val (realRef, realGeom, realSigpac) = SigpacApiService.recoverParcelaFromPoint(
                            parcelaToHydrate.centroidLat, 
                            parcelaToHydrate.centroidLng!!
                        )
                        
                        if (realRef != null) {
                            updatedParcela = updatedParcela.copy(
                                referencia = realRef,
                                geometryRaw = realGeom,
                                sigpacInfo = realSigpac
                            )
                        }
                    }

                    // Hidratación Normal (Datos Cultivo y Análisis IA)
                    // PASAMOS EL ÁREA PARA MATCHING DE CULTIVO
                    // SI EL ÁREA ES 0 (PUNTO), PASAMOS COORDENADAS PARA DESAMBIGUAR
                    val pointCheck = if(updatedParcela.area == 0.0 && updatedParcela.centroidLat != null && updatedParcela.centroidLng != null) {
                        Pair(updatedParcela.centroidLat, updatedParcela.centroidLng)
                    } else null

                    val (sigpac, cultivo, _) = SigpacApiService.fetchHydration(
                        updatedParcela.referencia, 
                        updatedParcela.area, 
                        pointCheck
                    )
                    
                    val finalSigpac = sigpac ?: updatedParcela.sigpacInfo
                    
                    val reportIA = GeminiService.analyzeParcela(updatedParcela.copy(sigpacInfo = finalSigpac, cultivoInfo = cultivo))
                    
                    updatedParcela = updatedParcela.copy(
                        sigpacInfo = finalSigpac,
                        cultivoInfo = cultivo,
                        informeIA = reportIA,
                        isHydrated = true
                    )

                    // Commit update
                    val updatedList = currentExpedientesState.value.map { e ->
                        if (e.id == targetExpId) {
                            e.copy(parcelas = e.parcelas.map { p -> if (p.id == parcelaToHydrate.id) updatedParcela else p })
                        } else e
                    }
                    onUpdateExpedientes(updatedList)
                    delay(400)
                }
            } catch (e: Exception) {
                Log.e("Hydration", "Error hydrating $targetExpId: ${e.message}")
            } finally {
                activeHydrations = activeHydrations - targetExpId
            }
        }
    }

    LaunchedEffect(expedientes) {
        expedientes.forEach { exp ->
            if (exp.parcelas.any { !it.isHydrated }) {
                startHydrationSequence(exp.id)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = KmlParser.getFileName(context, it) ?: "Expediente"
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
            }
        }
    }

    if (selectedExpedienteId != null) {
        val currentExp = expedientes.find { it.id == selectedExpedienteId }
        if (currentExp != null) {
            ProjectDetailsScreen(
                exp = currentExp, 
                onBack = { selectedExpedienteId = null }, 
                onLocate = onNavigateToMap, 
                onCamera = onOpenCamera,
                onUpdateExpediente = { updatedExp ->
                    onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
                }
            )
        } else {
            selectedExpedienteId = null
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenterAlignedTopAppBar(
                title = { Text("ESTACIÓN DE TRABAJO", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                modifier = Modifier.padding(16.dp).fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88))
                    Spacer(Modifier.width(12.dp))
                    Text("IMPORTAR CARTOGRAFÍA KML", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }

            Text("PROYECTOS ACTIVOS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray)

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(expedientes, key = { it.id }) { exp ->
                    ProjectListItem(
                        exp = exp, 
                        isActive = exp.id == activeProjectId,
                        onActivate = { onActivateProject(exp.id) },
                        onSelect = { selectedExpedienteId = exp.id }, 
                        onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                    )
                }
            }
        }
    }
}

// ... Resto de componentes (ProjectListItem, ProjectDetailsScreen, etc.) se mantienen igual ...
// Solo hemos modificado la función composable principal y su bloque startHydrationSequence
@Composable
fun ProjectListItem(
    exp: NativeExpediente, 
    isActive: Boolean,
    onActivate: () -> Unit,
    onSelect: () -> Unit, 
    onDelete: () -> Unit
) {
    val hydratedCount = exp.parcelas.count { it.isHydrated }
    val progress = if (exp.parcelas.isEmpty()) 0f else hydratedCount.toFloat() / exp.parcelas.size
    val animatedProgress by animateFloatAsState(targetValue = progress)
    val isComplete = progress >= 1.0f

    val backgroundColor = when {
        isComplete -> Color(0xFF00FF88).copy(alpha = 0.15f) 
        isActive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable { onSelect() }
            .animateContentSize(), 
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp, 
            if (isActive) Color(0xFF00FF88) else Color.White.copy(0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onActivate, modifier = Modifier.size(32.dp)) {
                    if (isActive) {
                         Icon(Icons.Default.RadioButtonChecked, null, tint = Color(0xFF00FF88), modifier = Modifier.size(24.dp))
                    } else {
                         Icon(Icons.Default.RadioButtonUnchecked, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if(isActive || isComplete) Color.White else Color.LightGray)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 14.sp)
                    if (isComplete) {
                        Text("COMPLETADO", color = Color(0xFF00FF88), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    } else if (isActive) {
                        Text("PROYECTO ACTIVO", color = Color(0xFF00FF88), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(24.dp)) }
            }
            AnimatedVisibility(visible = !isComplete, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Color(0xFF00FF88), trackColor = Color.White.copy(0.05f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailsScreen(
    exp: NativeExpediente, 
    onBack: () -> Unit, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateExpediente: (NativeExpediente) -> Unit
) {
    var isGroupedByExpediente by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val sortedParcelas = remember(exp.parcelas) { exp.parcelas.sortedBy { it.referencia } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(exp.titular, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (isGroupedByExpediente) "Agrupado por Nº Expediente" else "Listado Completo", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(28.dp)) } },
                actions = { IconButton(onClick = { isGroupedByExpediente = !isGroupedByExpediente }) { Icon(imageVector = if (isGroupedByExpediente) Icons.Default.AccountTree else Icons.Default.FormatListBulleted, contentDescription = "Agrupar", tint = if (isGroupedByExpediente) Color(0xFF00FF88) else Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val groupedParcelas = remember(sortedParcelas, isGroupedByExpediente) {
            if (isGroupedByExpediente) {
                sortedParcelas.groupBy { it.cultivoInfo?.expNum?.trim()?.ifEmpty { null } ?: "SIN_EXPEDIENTE" }
                    .toSortedMap { a, b -> when { a == "SIN_EXPEDIENTE" -> 1; b == "SIN_EXPEDIENTE" -> -1; else -> a.compareTo(b) } }
            } else null
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (isGroupedByExpediente && groupedParcelas != null) {
                groupedParcelas.forEach { (expNum, parcelasGroup) ->
                    val isExpanded = expandedGroups[expNum] ?: false
                    stickyHeader { ExpedienteHeader(expNum = if (expNum == "SIN_EXPEDIENTE") null else expNum, count = parcelasGroup.size, isExpanded = isExpanded, onToggle = { expandedGroups[expNum] = !isExpanded }) }
                    if (isExpanded) {
                        items(parcelasGroup, key = { it.id }) { parcela ->
                            NativeRecintoCard(parcela = parcela, onLocate = onLocate, onCamera = onCamera, onUpdateParcela = { updatedParcela -> onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })) })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            } else {
                items(sortedParcelas, key = { it.id }) { parcela ->
                    NativeRecintoCard(parcela = parcela, onLocate = onLocate, onCamera = onCamera, onUpdateParcela = { updatedParcela -> onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it })) })
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ExpedienteHeader(expNum: String?, count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    val isUnassigned = expNum == null
    val NeonGreen = Color(0xFF00FF88)
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onToggle() }, color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF252525)).border(1.dp, if(isUnassigned) Color.White.copy(0.1f) else NeonGreen.copy(0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(imageVector = if (isUnassigned) Icons.Default.Warning else Icons.Default.FolderOpen, contentDescription = null, tint = if (isUnassigned) Color.Gray else NeonGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text(text = if (isUnassigned) "SIN Nº EXPEDIENTE" else "EXP. $expNum", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = FontFamily.Monospace); if (isUnassigned) { Text(text = "Recintos no asociados a declaración", color = Color.LightGray, fontSize = 11.sp) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(text = "$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (isExpanded) "Colapsar" else "Expandir", tint = Color.White.copy(0.5f)) }
        }
    }
}
