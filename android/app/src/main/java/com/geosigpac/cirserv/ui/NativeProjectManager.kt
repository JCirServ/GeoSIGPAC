
package com.geosigpac.cirserv.ui

import android.util.Log
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
    
    // Consola de Logs en Pantalla
    val debugLogs = remember { mutableStateListOf<String>() }
    val logListState = rememberLazyListState()
    
    // CORRECCIÓN: Control de procesos de hidratación activos (Tipo explícito)
    var activeHydrations by remember { mutableStateOf<Set<String>>(setOf()) }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedMsg = "[$time] $msg"
        debugLogs.add(formattedMsg)
        Log.d("GeoSIGPAC_DEBUG", msg) // También al log del sistema
        if (debugLogs.size > 60) debugLogs.removeAt(0)
    }

    fun startHydrationSequence(targetExpId: String) {
        if (activeHydrations.contains(targetExpId)) return
        activeHydrations = activeHydrations + targetExpId
        
        scope.launch {
            addLog("Iniciando secuencia para EXP: $targetExpId")
            try {
                while (true) {
                    val currentList = currentExpedientesState.value
                    val exp = currentList.find { it.id == targetExpId } ?: break
                    val parcelaToHydrate = exp.parcelas.find { !it.isHydrated } 
                    
                    if (parcelaToHydrate == null) {
                        addLog("✓ Proyecto finalizado correctamente.")
                        break
                    }
                    
                    addLog("Consultando API para ${parcelaToHydrate.referencia}...")
                    
                    val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcelaToHydrate.referencia)
                    
                    if (sigpac != null) {
                        addLog("   [OK] Recinto: ${sigpac.usoSigpac} | ${sigpac.superficie} ha")
                    } else {
                        addLog("   [ERROR] No se recibió JSON de Recinto.")
                    }
                    
                    if (cultivo != null) {
                        addLog("   [OK] Cultivo: Exp ${cultivo.expNum}")
                    } else {
                        addLog("   [INFO] Sin cultivo declarado.")
                    }

                    addLog("   Generando dictamen IA...")
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
                    delay(400) // Evitar rate limiting
                }
            } catch (e: Exception) {
                addLog("   [CRITICAL ERROR] ${e.message}")
            } finally {
                activeHydrations = activeHydrations - targetExpId
            }
        }
    }

    // Asegurar que al entrar se analicen proyectos pendientes
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
            addLog("Archivo: $fileName")
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                addLog("Parseo KML OK: ${parcelas.size} recintos")
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
            } else {
                addLog("[ERROR] No se detectaron recintos en el archivo.")
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
                title = { Text("GESTIÓN TÉCNICA", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                modifier = Modifier.padding(20.dp).fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.UploadFile, null, tint = Color(0xFF00FF88))
                    Spacer(Modifier.width(12.dp))
                    Text("IMPORTAR KML / KMZ", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            Text("PROYECTOS", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(expedientes, key = { it.id }) { exp ->
                    ProjectListItem(exp, { selectedExpedienteId = exp.id }, { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) })
                }
            }

            // TERMINAL DE DEPUREACIÓN MEJORADA
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SISTEMA DE EVENTOS", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${activeHydrations.size} hilos activos", color = Color.Gray, fontSize = 8.sp)
                    }
                    Divider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(debugLogs.reversed()) { log ->
                            Text(
                                text = log, 
                                color = if (log.contains("[ERROR]")) Color(0xFFFF5252) else if (log.contains("[OK]")) Color(0xFF00FF88) else Color.LightGray,
                                fontSize = 10.sp, 
                                fontFamily = FontFamily.Monospace
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
    val animatedProgress by animateFloatAsState(targetValue = progress)

    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF00FF88), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${exp.parcelas.size} recintos", color = Color.Gray, fontSize = 10.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF00FF88), trackColor = Color.White.copy(0.05f))
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if(parcela.isHydrated) Color(0xFF00FF88).copy(0.2f) else Color.White.copy(0.05f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if(parcela.isHydrated) Color(0xFF00FF88) else Color.Yellow))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (isLoading) Text("Sincronizando...", color = Color.Gray, fontSize = 10.sp)
                }
                if (!isLoading) {
                    IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Yellow)
                }
            }

            if (expanded && parcela.isHydrated) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF00FF88).copy(0.08f)).padding(10.dp)) {
                        Row {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(parcela.informeIA ?: "Sin análisis", fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SIGPAC", color = Color.Yellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            DataField("USO", parcela.sigpacInfo?.usoSigpac ?: "-")
                            DataField("AREA", "${parcela.sigpacInfo?.superficie ?: "-"} ha")
                            DataField("PEND", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CULTIVO", color = Color(0xFF62D2FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            DataField("PRODUCTO", parcela.cultivoInfo?.parcProducto?.toString() ?: "-")
                            DataField("SISTEMA", parcela.cultivoInfo?.parcSistexp ?: "-")
                            DataField("AYUDA", parcela.cultivoInfo?.parcAyudasol ?: "-")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onLocate(parcela.lat, parcela.lng) }, modifier = Modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(8.dp)) {
                        Text("VER EN MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}
