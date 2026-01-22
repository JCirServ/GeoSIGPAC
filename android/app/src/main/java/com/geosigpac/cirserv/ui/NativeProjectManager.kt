
package com.geosigpac.cirserv.ui

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    val debugLogs = remember { mutableStateListOf<String>() }
    val logListState = rememberLazyListState()
    var activeHydrations by remember { mutableStateOf<Set<String>>(setOf()) }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugLogs.add("[$time] $msg")
        if (debugLogs.size > 60) debugLogs.removeAt(0)
    }

    fun startHydrationSequence(targetExpId: String) {
        if (activeHydrations.contains(targetExpId)) return
        activeHydrations = activeHydrations + targetExpId
        
        scope.launch {
            addLog("Analizando proyecto: $targetExpId")
            try {
                while (true) {
                    val currentList = currentExpedientesState.value
                    val exp = currentList.find { it.id == targetExpId } ?: break
                    val parcelaToHydrate = exp.parcelas.find { !it.isHydrated } 
                    
                    if (parcelaToHydrate == null) break
                    
                    val (sigpac, cultivo, centroid) = SigpacApiService.fetchHydration(parcelaToHydrate.referencia)
                    val reportIA = GeminiService.analyzeParcela(parcelaToHydrate)
                    
                    val updatedList = currentExpedientesState.value.map { e ->
                        if (e.id == targetExpId) {
                            e.copy(
                                parcelas = e.parcelas.map { p ->
                                    if (p.id == parcelaToHydrate.id) {
                                        p.copy(
                                            sigpacInfo = sigpac,
                                            cultivoInfo = cultivo,
                                            centroidLat = centroid?.first,
                                            centroidLng = centroid?.second,
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
                addLog("[ERROR] $targetExpId: ${e.message}")
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
            ProjectDetailsScreen(currentExp, { selectedExpedienteId = null }, onNavigateToMap, onOpenCamera)
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

            // Consola de Log
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("REGISTRO DE RED", color = Color(0xFF00FF88), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { debugLogs.clear() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                    Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(debugLogs.reversed()) { log ->
                            Text(text = log, color = if(log.contains("[ERROR]")) Color.Red else Color.LightGray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
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

    Card(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable { onSelect() }
            .animateContentSize(), // Permite que el cambio de tamaño sea suave
        colors = CardDefaults.cardColors(containerColor = if(isActive) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface.copy(alpha=0.6f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp, 
            if (isActive) Color(0xFF00FF88) else Color.White.copy(0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indicador de Estado / Activación
                IconButton(onClick = onActivate, modifier = Modifier.size(32.dp)) {
                    if (isActive) {
                         Icon(Icons.Default.RadioButtonChecked, null, tint = Color(0xFF00FF88), modifier = Modifier.size(24.dp))
                    } else {
                         Icon(Icons.Default.RadioButtonUnchecked, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if(isActive) Color.White else Color.LightGray)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 14.sp)
                    if (isActive) {
                        Text("PROYECTO ACTIVO", color = Color(0xFF00FF88), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
                
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(24.dp)) }
            }
            
            // Barra de progreso visible solo si no está completa
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(exp: NativeExpediente, onBack: () -> Unit, onLocate: (String) -> Unit, onCamera: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(exp.titular, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(28.dp)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(exp.parcelas, key = { it.id }) { parcela ->
                NativeRecintoCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun NativeRecintoCard(parcela: NativeParcela, onLocate: (String) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) } 
    var selectedTab by remember { mutableIntStateOf(0) }
    val isLoading = !parcela.isHydrated

    // Cálculo de Análisis Agronómico
    val agroAnalysis = remember(parcela) {
        if (parcela.isHydrated) {
            val prodDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.parcProducto?.toString())
            SigpacCodeManager.performAgroAnalysis(
                productoCode = parcela.cultivoInfo?.parcProducto,
                productoDesc = prodDesc,
                sigpacUso = parcela.sigpacInfo?.usoSigpac,
                ayudasRaw = parcela.cultivoInfo?.parcAyudasol,
                pdrRaw = parcela.cultivoInfo?.pdrRec,
                sistExp = parcela.cultivoInfo?.parcSistexp,
                coefRegadio = parcela.sigpacInfo?.coefRegadio
            )
        } else null
    }

    // Determinar Estado Visual (Semáforo)
    val statusColor = remember(agroAnalysis, isLoading) {
        when {
            isLoading -> Color.Gray
            agroAnalysis == null -> Color.Gray
            !agroAnalysis.isCompatible -> Color(0xFFFF5252) // ERROR (Rojo)
            agroAnalysis.explanation.contains("AVISO", ignoreCase = true) || agroAnalysis.explanation.contains("Nota", ignoreCase = true) -> Color(0xFFFF9800) // WARNING (Naranja)
            else -> Color(0xFF00FF88) // OK (Verde)
        }
    }

    val statusIcon = remember(agroAnalysis, isLoading) {
         when {
            isLoading -> null
            agroAnalysis == null -> null
            !agroAnalysis.isCompatible -> Icons.Default.Error
            agroAnalysis.explanation.contains("AVISO", ignoreCase = true) -> Icons.Default.Warning
            else -> null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = if(parcela.isHydrated) 0.5f else 0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de Estado (Punto de color)
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if(isLoading) Color.Yellow else statusColor))
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                    if (isLoading) {
                        Text("Cargando atributos...", color = Color.Gray, fontSize = 14.sp)
                    } else if (agroAnalysis != null && statusIcon != null) {
                         // Mostrar texto de alerta resumido en cabecera si hay problema
                         val alertText = if(!agroAnalysis.isCompatible) "INCOMPATIBLE / ERROR" else "AVISO AGRONÓMICO"
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

            if (expanded && parcela.isHydrated) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    
                    // IA REPORT (Compacto)
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.03f)).padding(12.dp)) {
                         Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF62D2FF), modifier = Modifier.size(18.dp).padding(top=2.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(parcela.informeIA ?: "Sin análisis disponible", fontSize = 14.sp, lineHeight = 18.sp, color = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- ANÁLISIS TÉCNICO ---
                    if (agroAnalysis != null) {
                        // Banner Compatibilidad / Error
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(0.1f))
                                .border(1.dp, statusColor.copy(0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if(!agroAnalysis.isCompatible) Icons.Default.Error else if(statusColor == Color(0xFFFF9800)) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null, 
                                        tint = statusColor, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            !agroAnalysis.isCompatible -> "INCOMPATIBLE / ERROR"
                                            statusColor == Color(0xFFFF9800) -> "AVISO AGRONÓMICO"
                                            else -> "USO COMPATIBLE"
                                        }, 
                                        fontWeight = FontWeight.Black, 
                                        fontSize = 15.sp,
                                        color = statusColor
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(agroAnalysis.explanation, fontSize = 14.sp, color = Color.White)
                            }
                        }

                        // Requisitos de Campo (Checklist)
                        if (agroAnalysis.requirements.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("REQUISITOS DE CAMPO (GUÍA)", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                            Spacer(Modifier.height(6.dp))
                            agroAnalysis.requirements.forEach { req ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(0.3f))
                                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(req.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF62D2FF))
                                        Spacer(Modifier.height(4.dp))
                                        Text(req.requirement, fontSize = 14.sp, color = Color.LightGray, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // PESTAÑAS (Recinto / Cultivo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(0.05f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tab Recinto
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
                        
                        // Tab Cultivo
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
                        // DATOS RECINTO
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
                        // DATOS CULTIVO
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

                             // Ayudas Solicitadas
                            if (!parcela.cultivoInfo?.parcAyudasol.isNullOrEmpty()) {
                                AyudasStaticList("AYUDA SOL", parcela.cultivoInfo?.parcAyudasol)
                            } else {
                                DataField("AYUDA SOL", "-")
                            }

                            // Ayudas PDR
                            if (!parcela.cultivoInfo?.pdrRec.isNullOrEmpty()) {
                                AyudasStaticList("AYUDAS PDR", parcela.cultivoInfo?.pdrRec, isPdr = true)
                            } else {
                                DataField("AYUDAS PDR", "-")
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.1f))

                             val prodSecDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.cultsecunProducto?.toString())
                            DataField("PROD. SEC", prodSecDesc ?: "-")
                            
                            // Ayudas Cultivo Secundario
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
