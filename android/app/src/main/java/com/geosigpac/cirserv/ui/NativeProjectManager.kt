
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeProjectManager(
    expedientes: List<NativeExpediente>,
    onUpdateExpedientes: (List<NativeExpediente>) -> Unit,
    onNavigateToMap: (Double?, Double?) -> Unit,
    onOpenCamera: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedExpedienteId by remember { mutableStateOf<String?>(null) }
    val currentExpedientesState = rememberUpdatedState(expedientes)
    
    // Consola de Logs
    val debugLogs = remember { mutableStateListOf<String>() }
    val logListState = rememberLazyListState()
    
    // Control de procesos de hidratación activos para no duplicar
    val activeHydrations = remember { mutableStateSetOf<String>() }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugLogs.add("[$time] $msg")
        if (debugLogs.size > 50) debugLogs.removeAt(0)
    }

    fun startHydrationSequence(targetExpId: String) {
        if (activeHydrations.contains(targetExpId)) return
        activeHydrations.add(targetExpId)
        
        scope.launch {
            addLog("Iniciando análisis para: $targetExpId")
            while (true) {
                val currentList = currentExpedientesState.value
                val exp = currentList.find { it.id == targetExpId } ?: break
                val parcelaToHydrate = exp.parcelas.find { !it.isHydrated } 
                
                if (parcelaToHydrate == null) {
                    addLog("Proyecto completado: $targetExpId")
                    break
                }
                
                addLog("-> Cargando recinto ${parcelaToHydrate.referencia}")
                
                try {
                    val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcelaToHydrate.referencia)
                    
                    if (sigpac != null) {
                        addLog("   [OK] Atributos Recinto: Uso ${sigpac.usoSigpac}, ${sigpac.superficie}ha")
                    } else {
                        addLog("   [AVISO] No hay respuesta de Recinto (Verifica conexión)")
                    }
                    
                    if (cultivo != null) {
                        addLog("   [OK] Cultivo Declarado: Exp ${cultivo.expNum}")
                    } else {
                        addLog("   [INFO] Sin cultivo declarado activo.")
                    }

                    addLog("   Solicitando dictamen a IA...")
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
                } catch (e: Exception) {
                    addLog("   [ERROR] Excepción: ${e.localizedMessage}")
                }
                
                delay(500) // Pausa de cortesía para no saturar
            }
            activeHydrations.remove(targetExpId)
        }
    }

    // AUTO-HIDRATACIÓN: Al cargar expedientes guardados, arranca el proceso
    LaunchedEffect(expedientes.size) {
        expedientes.forEach { exp ->
            if (exp.parcelas.any { !it.isHydrated }) {
                startHydrationSequence(exp.id)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = KmlParser.getFileName(context, it) ?: "Proyecto_${System.currentTimeMillis()}"
            addLog("Archivo: $fileName")
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                addLog("Cartografía: ${parcelas.size} recintos detectados")
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
                startHydrationSequence(newExp.id)
            } else {
                addLog("[ERROR] KML vacío o corrupto.")
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
                title = { Text("ESTACIÓN DE TRABAJO", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                modifier = Modifier.padding(20.dp).fillMaxWidth().height(120.dp).clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(32.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("IMPORTAR CARTOGRAFÍA", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text("KML o KMZ de inspección", color = Color.Gray, fontSize = 10.sp)
                }
            }

            Text("PROYECTOS ACTIVOS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(expedientes, key = { it.id }) { exp ->
                    ProjectListItem(
                        exp = exp, 
                        onSelect = { selectedExpedienteId = exp.id }, 
                        onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                    )
                }
                
                if (expedientes.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No hay proyectos cargados", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }

            // TERMINAL DE DEPUREACIÓN
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("RED Y PROCESOS", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { debugLogs.clear() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                    Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(debugLogs.reversed()) { log ->
                            Text(
                                text = log, 
                                color = if (log.contains("[ERROR]")) Color(0xFFFF5252) else if (log.contains("[OK]")) Color(0xFF00FF88) else Color.LightGray,
                                fontSize = 10.sp, 
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(exp: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    val hydratedCount = exp.parcelas.count { it.isHydrated }
    val progress = if (exp.parcelas.isEmpty()) 0f else hydratedCount.toFloat() / exp.parcelas.size
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(Color(0xFF00FF88).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF00FF88), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${exp.parcelas.size} recintos", color = Color.Gray, fontSize = 10.sp)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray.copy(0.4f), modifier = Modifier.size(18.dp)) }
            }
            
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = if (progress == 1f) Color(0xFF00FF88) else MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(0.05f)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(progress == 1f) "Sincronizado" else "Sincronizando...", color = if(progress == 1f) Color(0xFF00FF88) else Color.Gray, fontSize = 9.sp)
                Text("${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(exp: NativeExpediente, onBack: () -> Unit, onLocate: (Double?, Double?) -> Unit, onCamera: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(exp.titular, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(exp.parcelas, key = { it.id }) { parcela ->
                NativeRecintoCard(parcela, { lat, lng -> onLocate(lat, lng) }, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun NativeRecintoCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) } 
    val isLoading = !parcela.isHydrated

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if(parcela.isHydrated) Color(0xFF00FF88).copy(0.2f) else MaterialTheme.colorScheme.outline.copy(0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if(parcela.isHydrated) Color(0xFF00FF88) else Color(0xFFFFD700)))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (isLoading) Text("Pendiente...", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                }
                if (!isLoading) {
                    IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (expanded && parcela.isHydrated) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF00FF88).copy(0.08f)).padding(10.dp)) {
                        Row {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(parcela.informeIA ?: "Sin datos IA", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SIGPAC", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("USO", parcela.sigpacInfo?.usoSigpac ?: "-")
                            DataField("AREA", "${parcela.sigpacInfo?.superficie ?: "-"} ha")
                            DataField("PEND", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %")
                            DataField("ALT", "${parcela.sigpacInfo?.altitud ?: "-"} m")
                        }
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp).padding(horizontal = 12.dp), color = Color.White.copy(0.05f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CULTIVO", color = Color(0xFF62D2FF), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("PRODUCTO", parcela.cultivoInfo?.parcProducto?.toString() ?: "-")
                            DataField("SISTEMA", parcela.cultivoInfo?.parcSistexp ?: "-")
                            DataField("SUP CULT", "${parcela.cultivoInfo?.parcSupcult ?: "-"} m²")
                            DataField("AYUDA", parcela.cultivoInfo?.parcAyudasol ?: "-")
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("VISOR SIGPAC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
