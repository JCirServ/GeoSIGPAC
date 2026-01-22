
package com.geosigpac.cirserv.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
                    
                    val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcelaToHydrate.referencia)
                    val reportIA = GeminiService.analyzeParcela(parcelaToHydrate)
                    
                    val updatedList = currentExpedientesState.value.map { e ->
                        if (e.id == targetExpId) {
                            e.copy(
                                parcelas = e.parcelas.map { p ->
                                    if (p.id == parcelaToHydrate.id) {
                                        p.copy(
                                            sigpacInfo = sigpac,
                                            cultivoInfo = cultivo,
                                            informeIA = reportIA,
                                            isHydrated = true
                                        )
                                    } else p
                                }
                            )
                        } else e
                    }
                    onUpdateExpedientes(updatedList)
                    delay(300)
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
                title = { Text("ESTACIÓN DE TRABAJO", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp) },
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
                    Text("IMPORTAR CARTOGRAFÍA KML", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            Text("PROYECTOS ACTIVOS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)

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
                        Text("REGISTRO DE RED", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { debugLogs.clear() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                    Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(debugLogs.reversed()) { log ->
                            Text(text = log, color = if(log.contains("[ERROR]")) Color.Red else Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
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

    Card(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable { onSelect() },
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
                IconButton(onClick = onActivate, modifier = Modifier.size(24.dp)) {
                    if (isActive) {
                         Icon(Icons.Default.RadioButtonChecked, null, tint = Color(0xFF00FF88))
                    } else {
                         Icon(Icons.Default.RadioButtonUnchecked, null, tint = Color.Gray)
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if(isActive) Color.White else Color.LightGray)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 10.sp)
                    if (isActive) {
                        Text("PROYECTO ACTIVO", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = Color(0xFF00FF88), trackColor = Color.White.copy(0.05f))
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
                title = { Text(exp.titular, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
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
    val isLoading = !parcela.isHydrated

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if(parcela.isHydrated) Color(0xFF00FF88).copy(0.2f) else Color.White.copy(0.05f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if(parcela.isHydrated) Color(0xFF00FF88) else Color.Yellow))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    if (isLoading) Text("Cargando atributos...", color = Color.Gray, fontSize = 10.sp)
                }
                if (!isLoading) {
                    IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Yellow)
                }
            }

            if (expanded && parcela.isHydrated) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // IA REPORT
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF00FF88).copy(0.08f)).border(1.dp, Color(0xFF00FF88).copy(0.2f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Row {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(parcela.informeIA ?: "Sin análisis disponible", fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // GRID TÉCNICO EXCLUSIVO
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        // Columna RECINTO (Izquierda)
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("ATRIB. RECINTO", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("USO SIGPAC", parcela.sigpacInfo?.usoSigpac ?: "-")
                            DataField("SUPERFICIE", "${parcela.sigpacInfo?.superficie ?: "-"} ha")
                            DataField("PENDIENTE", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %")
                            DataField("ALTITUD", "${parcela.sigpacInfo?.altitud ?: "-"} m")
                            DataField("ADMISIBILIDAD", "${parcela.sigpacInfo?.admisibilidad ?: "-"}")
                            DataField("REGIÓN", parcela.sigpacInfo?.region ?: "-")
                            DataField("COEF. REGADÍO", "${parcela.sigpacInfo?.coefRegadio ?: "-"}")
                        }
                        
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color.White.copy(0.05f))
                        
                        // Columna CULTIVO (Derecha)
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("CULTIVO DECLARADO", color = Color(0xFF62D2FF), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("EXP. NUM", parcela.cultivoInfo?.expNum ?: "-")
                            DataField("PRODUCTO", parcela.cultivoInfo?.parcProducto?.toString() ?: "-")
                            DataField("SIST. EXP", parcela.cultivoInfo?.parcSistexp ?: "-")
                            DataField("SUP. CULT", "${parcela.cultivoInfo?.parcSupcult ?: "-"} m²")
                            
                            // Reemplazamos campos simples por Dropdowns si hay contenido, o texto simple si no.
                            if (!parcela.cultivoInfo?.parcAyudasol.isNullOrEmpty()) {
                                AyudasDropdown("AYUDA SOL", parcela.cultivoInfo?.parcAyudasol)
                            } else {
                                DataField("AYUDA SOL", "-")
                            }

                            DataField("PDR REC", parcela.cultivoInfo?.pdrRec ?: "-")
                            DataField("PROD. SEC", parcela.cultivoInfo?.cultsecunProducto?.toString() ?: "-")
                            
                            if (!parcela.cultivoInfo?.cultsecunAyudasol.isNullOrEmpty()) {
                                AyudasDropdown("AYUDA SEC", parcela.cultivoInfo?.cultsecunAyudasol)
                            } else {
                                DataField("AYUDA SEC", "-")
                            }
                            
                            DataField("IND. CULT", parcela.cultivoInfo?.parcIndcultapro?.toString() ?: "-")
                            DataField("APROVECHA", parcela.cultivoInfo?.tipoAprovecha ?: "-")
                        }
                    }

                    if (!parcela.sigpacInfo?.incidencias.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        IncidenciasDropdown(parcela.sigpacInfo?.incidencias)
                    }
                    
                    Spacer(Modifier.height(16.dp))
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
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LOCALIZAR EN MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun IncidenciasDropdown(rawIncidencias: String?) {
    var expanded by remember { mutableStateOf(false) }
    val incidenciasList = remember(rawIncidencias) {
        SigpacCodeManager.getFormattedIncidencias(rawIncidencias)
    }

    if (incidenciasList.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INCIDENCIAS (${incidenciasList.size})",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Divider(color = Color.White.copy(0.1f))
                Column(modifier = Modifier.padding(8.dp)) {
                    incidenciasList.forEach { incidencia ->
                        Text(
                            text = "• $incidencia",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AyudasDropdown(label: String, rawAyudas: String?) {
    var expanded by remember { mutableStateOf(false) }
    val ayudasList = remember(rawAyudas) {
        SigpacCodeManager.getFormattedAyudas(rawAyudas)
    }

    if (ayudasList.isNotEmpty()) {
         Column(modifier = Modifier.padding(vertical = 4.dp)) {
             Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .background(Color.White.copy(0.05f), RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     Text(
                        text = if (expanded) "Ocultar" else "${ayudasList.size} línea(s)",
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold, 
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                    ayudasList.forEach { ayuda ->
                        Text(
                            text = "• $ayuda",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
         }
    } else {
        DataField(label, "-")
    }
}

@Composable
fun DataField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
