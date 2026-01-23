
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
                    
                    // Fetch completo incluyendo geometría si faltaba
                    val result = SigpacApiService.fetchHydration(parcelaToHydrate.referencia)
                    val reportIA = GeminiService.analyzeParcela(parcelaToHydrate)
                    
                    val updatedList = currentExpedientesState.value.map { e ->
                        if (e.id == targetExpId) {
                            e.copy(
                                parcelas = e.parcelas.map { p ->
                                    if (p.id == parcelaToHydrate.id) {
                                        p.copy(
                                            sigpacInfo = result.sigpacData,
                                            cultivoInfo = result.cultivoData,
                                            centroidLat = result.centroid?.first,
                                            centroidLng = result.centroid?.second,
                                            // Si venía de KML Point (null), usar geometryRaw descargado. Si ya tenía (KML Polygon), mantenerlo.
                                            geometryRaw = p.geometryRaw ?: result.geometryRaw,
                                            informeIA = reportIA,
                                            isHydrated = true
                                        )
                                    } else p
                                }
                            )
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
            
            AnimatedVisibility(
                visible = !isComplete,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress }, 
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), 
                        color = Color(0xFF00FF88), 
                        trackColor = Color.White.copy(0.05f)
                    )
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(exp.titular, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isGroupedByExpediente) "Agrupado por Nº Expediente" else "Listado Completo", 
                            fontSize = 12.sp, 
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(28.dp)) } },
                actions = {
                    IconButton(onClick = { isGroupedByExpediente = !isGroupedByExpediente }) {
                        Icon(
                            imageVector = if (isGroupedByExpediente) Icons.Default.AccountTree else Icons.Default.FormatListBulleted,
                            contentDescription = "Agrupar",
                            tint = if (isGroupedByExpediente) Color(0xFF00FF88) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val groupedParcelas = remember(exp.parcelas, isGroupedByExpediente) {
            if (isGroupedByExpediente) {
                exp.parcelas.groupBy { it.cultivoInfo?.expNum?.trim()?.ifEmpty { null } ?: "SIN_EXPEDIENTE" }
                    .toSortedMap { a, b ->
                        when {
                            a == "SIN_EXPEDIENTE" -> 1
                            b == "SIN_EXPEDIENTE" -> -1
                            else -> a.compareTo(b)
                        }
                    }
            } else {
                null
            }
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (isGroupedByExpediente && groupedParcelas != null) {
                groupedParcelas.forEach { (expNum, parcelasGroup) ->
                    val isExpanded = expandedGroups[expNum] ?: false
                    stickyHeader {
                        ExpedienteHeader(
                            expNum = if (expNum == "SIN_EXPEDIENTE") null else expNum, 
                            count = parcelasGroup.size,
                            isExpanded = isExpanded,
                            onToggle = { expandedGroups[expNum] = !isExpanded }
                        )
                    }
                    if (isExpanded) {
                        items(parcelasGroup, key = { it.id }) { parcela ->
                            NativeRecintoCard(
                                parcela = parcela, 
                                onLocate = onLocate, 
                                onCamera = onCamera,
                                onUpdateParcela = { updatedParcela ->
                                    onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }))
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            } else {
                items(exp.parcelas, key = { it.id }) { parcela ->
                    NativeRecintoCard(
                        parcela = parcela, 
                        onLocate = onLocate, 
                        onCamera = onCamera,
                        onUpdateParcela = { updatedParcela ->
                            onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }))
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ExpedienteHeader(
    expNum: String?, 
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val isUnassigned = expNum == null
    val NeonGreen = Color(0xFF00FF88)
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onToggle() },
        color = MaterialTheme.colorScheme.background, 
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF252525)) 
                .border(1.dp, if(isUnassigned) Color.White.copy(0.1f) else NeonGreen.copy(0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isUnassigned) Icons.Default.Warning else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (isUnassigned) Color.Gray else NeonGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isUnassigned) "SIN Nº EXPEDIENTE" else "EXP. $expNum",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isUnassigned) {
                        Text(text = "Recintos no asociados a declaración", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(text = "$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = Color.White.copy(0.5f)
                )
            }
        }
    }
}

// --- NATIVE RECINTO CARD ---
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
    var isTechExpanded by remember { mutableStateOf(initiallyTechExpanded) }
    
    val NeonGreen = Color(0xFF00FF88)
    val DarkSurface = Color(0xFF1E2124)
    val hasPhotos = parcela.photos.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, if (isExpanded) NeonGreen.copy(0.5f) else Color.White.copy(0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // CABECERA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = parcela.referencia,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        if (parcela.uso != "N/D") {
                            Text(
                                text = "Uso: ${parcela.uso} • ${parcela.area} ha",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = { onCamera(parcela.id) },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hasPhotos) NeonGreen.copy(0.1f) else Color.White.copy(0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Cámara",
                        tint = if (hasPhotos) NeonGreen else Color.Gray
                    )
                }
            }

            // CONTENIDO EXPANDIDO
            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                
                // 1. ANÁLISIS IA (Si existe)
                if (!parcela.informeIA.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A2A35), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Yellow.copy(0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SmartToy, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("ANÁLISIS TÉCNICO IA", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(parcela.informeIA, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 2. FOTOS (Si existen)
                if (hasPhotos) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(100.dp)
                    ) {
                        items(parcela.photos) { photoUri ->
                            AsyncImage(
                                model = Uri.parse(photoUri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(100.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 3. DATOS TÉCNICOS EXPANDIBLES
                if (parcela.isHydrated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTechExpanded = !isTechExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if(isTechExpanded) "Ocultar Datos Técnicos" else "Ver Datos SIGPAC y Declaración",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = if(isTechExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isTechExpanded) {
                        Column(modifier = Modifier.background(Color.Black.copy(0.2f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            // COLUMNA IZQ: SIGPAC
                            Text("SIGPAC", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                            TechRow("Uso", parcela.sigpacInfo?.usoSigpac ?: "---")
                            TechRow("Pendiente", "${parcela.sigpacInfo?.pendienteMedia ?: 0}%")
                            TechRow("Regadío", "${parcela.sigpacInfo?.coefRegadio ?: 0}%")
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // COLUMNA DER: DECLARACIÓN
                            Text("DECLARACIÓN PAC", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                            if (parcela.cultivoInfo != null) {
                                TechRow("Producto", "${parcela.cultivoInfo.parcProducto} - ${parcela.cultivoInfo.parcProducto}") // Simplificado
                                TechRow("Régimen", parcela.cultivoInfo.parcSistexp ?: "---")
                                if (!parcela.cultivoInfo.parcAyudasol.isNullOrEmpty()) {
                                    TechRow("Ayuda", "SÍ")
                                }
                            } else {
                                Text("Sin declaración asociada", color = Color(0xFFEF5350), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Descargando datos oficiales...", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 4. ACCIONES
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onLocate(parcela.referencia) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Localizar")
                    }
                }
            }
        }
    }
}

@Composable
fun TechRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
