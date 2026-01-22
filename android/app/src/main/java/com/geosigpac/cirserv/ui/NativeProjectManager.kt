
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
fun ProjectDetailsScreen(
    exp: NativeExpediente, 
    onBack: () -> Unit, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateExpediente: (NativeExpediente) -> Unit
) {
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

@Composable
fun NativeRecintoCard(
    parcela: NativeParcela, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateParcela: (NativeParcela) -> Unit
) {
    var expanded by remember { mutableStateOf(false) } 
    // Secciones internas colapsadas por defecto
    var inspectionExpanded by remember { mutableStateOf(false) }
    var dataExpanded by remember { mutableStateOf(false) }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val isLoading = !parcela.isHydrated
    
    // Lista de usos comunes para el desplegable
    val commonUsos = remember { SigpacCodeManager.getCommonUsos() }
    var showUsoDropdown by remember { mutableStateOf(false) }

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
                coefRegadio = parcela.sigpacInfo?.coefRegadio,
                pendienteMedia = parcela.sigpacInfo?.pendienteMedia
            )
        } else null
    }
    
    // --- LÓGICA DE AUTOCOMPLETADO DE USO ---
    LaunchedEffect(agroAnalysis) {
        if (agroAnalysis != null && parcela.verifiedUso == null) {
             val sigpacUsoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull() // "TA (TIERRAS..)" -> "TA"
             if (agroAnalysis.isCompatible && !agroAnalysis.explanation.contains("AVISO") && sigpacUsoRaw != null) {
                 onUpdateParcela(parcela.copy(verifiedUso = sigpacUsoRaw))
             }
        }
    }

    // --- ESTADO DE COMPLETADO GLOBAL ---
    val isFullyCompleted = remember(parcela.finalVerdict) {
        parcela.finalVerdict != null
    }

    // Determinar Estado Visual (Semáforo)
    val statusColor = remember(agroAnalysis, isLoading, isFullyCompleted) {
        when {
            isLoading -> Color.Gray
            isFullyCompleted -> Color(0xFF62D2FF) // AZUL/CYAN = VERIFICADO OK
            agroAnalysis == null -> Color.Gray
            !agroAnalysis.isCompatible -> Color(0xFFFF5252) // ERROR (Rojo)
            agroAnalysis.explanation.contains("AVISO", ignoreCase = true) || agroAnalysis.explanation.contains("Nota", ignoreCase = true) -> Color(0xFFFF9800) // WARNING (Naranja)
            else -> Color(0xFF00FF88) // Compatible pero no verificado (Verde)
        }
    }

    val statusIcon = remember(agroAnalysis, isLoading, isFullyCompleted) {
         when {
            isLoading -> null
            isFullyCompleted -> Icons.Default.Verified
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
        border = BorderStroke(if(isFullyCompleted) 2.dp else 1.dp, statusColor.copy(alpha = if(parcela.isHydrated) 0.8f else 0.1f))
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
                    } else if (isFullyCompleted) {
                        Text("VERIFICADO: ${parcela.finalVerdict?.replace("_", " ")}", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            if (expanded && parcela.isHydrated && agroAnalysis != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    
                    // --- SECCIÓN 1: INSPECCIÓN VISUAL (Collapsible) ---
                    CollapsibleHeader("INSPECCIÓN VISUAL & REQUISITOS", inspectionExpanded) { inspectionExpanded = !inspectionExpanded }
                    
                    if (inspectionExpanded) {
                        // 1.1 Bloque de Discrepancia
                         Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(0.3f))
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                             Column {
                                 // Mostrar Discrepancia Explicita o Aviso
                                 val isWarning = agroAnalysis.explanation.contains("AVISO", ignoreCase = true) || agroAnalysis.explanation.contains("Nota", ignoreCase = true)
                                 
                                 if (!agroAnalysis.isCompatible || isWarning) {
                                     val title = if (!agroAnalysis.isCompatible) "DISCREPANCIA DETECTADA" else "AVISO DE COHERENCIA"
                                     val color = if (!agroAnalysis.isCompatible) Color(0xFFFF5252) else Color(0xFFFF9800)

                                     Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                     Spacer(Modifier.height(4.dp))
                                     
                                     val prodDesc = SigpacCodeManager.getProductoDescription(parcela.cultivoInfo?.parcProducto?.toString()) ?: "Desconocido"
                                     val sigpacUso = parcela.sigpacInfo?.usoSigpac ?: "Desconocido"
                                     
                                     Text("• SIGPAC: $sigpacUso", color = Color.White, fontSize = 13.sp)
                                     Text("• Declarado: $prodDesc", color = Color.White, fontSize = 13.sp)
                                     Spacer(Modifier.height(4.dp))
                                     Text(agroAnalysis.explanation, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                     Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical=8.dp))
                                 }

                                 // Checkbox implícito: Seleccionar uso
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     val isUsoChecked = parcela.verifiedUso != null
                                     Icon(
                                         imageVector = if(isUsoChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                         contentDescription = null,
                                         tint = if(isUsoChecked) Color(0xFF00FF88) else Color.Gray,
                                         modifier = Modifier.size(24.dp)
                                     )
                                     Spacer(Modifier.width(12.dp))
                                     
                                     // Desplegable de Uso
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

                                 // CHECKLIST ADICIONAL: PENDIENTE > 10%
                                 // LOGICA: Solo mostrar si pendiente > 10. Si es pasto, solo mostrar si hay discrepancia.
                                 val slope = parcela.sigpacInfo?.pendienteMedia ?: 0.0
                                 val usoRaw = parcela.sigpacInfo?.usoSigpac?.split(" ")?.firstOrNull()?.uppercase() ?: ""
                                 val isPastos = listOf("PS", "PR", "PA").contains(usoRaw)
                                 
                                 // Si es pasto y es compatible, NO mostramos la condición de pendiente.
                                 // Si es pasto pero hay error (no compatible), SÍ mostramos la condición.
                                 // Si no es pasto y supera el 10%, SIEMPRE mostramos la condición.
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
                             }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 1.2 REQUISITOS (CHECKLISTS DINÁMICOS)
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

                        // 1.3 VEREDICTO FINAL
                        Spacer(Modifier.height(12.dp))
                        Text("VEREDICTO FINAL", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // CUMPLE
                            Button(
                                onClick = { onUpdateParcela(parcela.copy(finalVerdict = "CUMPLE")) },
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

                            // NO CUMPLE
                            Button(
                                onClick = { onUpdateParcela(parcela.copy(finalVerdict = "NO_CUMPLE")) },
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
                    }

                    Spacer(Modifier.height(20.dp))
                    Divider(color = Color.White.copy(0.1f))
                    Spacer(Modifier.height(20.dp))

                    // --- SECCIÓN 2: DATOS TÉCNICOS (Collapsible) ---
                    CollapsibleHeader("DATOS TÉCNICOS & GEOMÉTRICOS", dataExpanded) { dataExpanded = !dataExpanded }

                    if (dataExpanded) {
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
fun CollapsibleHeader(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Gray)
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null,
            tint = Color.Gray
        )
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
