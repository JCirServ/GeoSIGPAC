
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.services.GeminiService
import com.geosigpac.cirserv.services.SigpacApiService
import com.geosigpac.cirserv.utils.KmlParser
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
    var selectedExpediente by remember { mutableStateOf<NativeExpediente?>(null) }

    // Referencia actualizada del estado para el proceso de hidratación
    val currentExpedientesState = rememberUpdatedState(expedientes)

    // Función para hidratar parcelas de forma reactiva
    fun startHydrationSequence(targetExpId: String) {
        scope.launch {
            // Buscamos el expediente inicial
            val targetExp = currentExpedientesState.value.find { it.id == targetExpId } ?: return@launch
            
            // Iteramos sobre las parcelas que faltan por hidratar
            targetExp.parcelas.forEach { parcela ->
                if (!parcela.isHydrated) {
                    // 1. Fetch de datos externos
                    val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcela.referencia)
                    val reportIA = GeminiService.analyzeParcela(parcela)
                    
                    // 2. Creamos una nueva lista inmutable con los datos actualizados
                    // Esto fuerza a Compose a detectar el cambio de referencia y recomponer
                    val updatedList = currentExpedientesState.value.map { exp ->
                        if (exp.id == targetExpId) {
                            exp.copy(
                                parcelas = exp.parcelas.map { p ->
                                    if (p.id == parcela.id) {
                                        p.copy(
                                            sigpacInfo = sigpac,
                                            cultivoInfo = cultivo,
                                            informeIA = reportIA,
                                            isHydrated = true
                                        )
                                    } else p
                                }
                            )
                        } else exp
                    }
                    
                    // 3. Notificamos al estado global (MainActivity -> Storage)
                    onUpdateExpedientes(updatedList)
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = KmlParser.getFileName(context, it) ?: "Nuevo Proyecto"
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                
                val newList = listOf(newExp) + expedientes
                onUpdateExpedientes(newList)
                
                // Iniciamos la secuencia de hidratación para el nuevo expediente
                startHydrationSequence(newExp.id)
            }
        }
    }

    if (selectedExpediente != null) {
        // Buscamos la versión más reciente del expediente seleccionado de la lista global
        val currentSelected = expedientes.find { it.id == selectedExpediente?.id } ?: selectedExpediente!!
        ProjectDetailsScreen(currentSelected, { selectedExpediente = null }, onNavigateToMap, onOpenCamera)
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenterAlignedTopAppBar(
                title = { Text("ESTACIÓN DE TRABAJO", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                modifier = Modifier.padding(20.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(32.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("IMPORTAR CARTOGRAFÍA KML", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text("Análisis técnico inmediato", color = Color.Gray, fontSize = 10.sp)
                }
            }

            Text("PROYECTOS ACTIVOS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(expedientes, key = { it.id }) { exp ->
                    ProjectListItem(
                        exp = exp, 
                        onSelect = { selectedExpediente = exp }, 
                        onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                    )
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
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFF00FF88).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF00FF88), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray.copy(0.4f)) }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HIDRATACIÓN SIGPAC", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (progress == 1f) Color(0xFF00FF88) else MaterialTheme.colorScheme.secondary,
                        trackColor = Color.White.copy(0.05f)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text("${(progress * 100).toInt()}%", color = if (progress == 1f) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            
            if (hydratedCount < exp.parcelas.size) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizando parcelas...", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Análisis completado", modifier = Modifier.padding(top = 8.dp), color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if(parcela.isHydrated) Color(0xFF00FF88).copy(0.2f) else MaterialTheme.colorScheme.outline.copy(0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if(parcela.isHydrated) Color(0xFF00FF88) else Color(0xFFFFD700)))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (isLoading) Text("Sincronizando...", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray) }
                }
            }

            if (expanded && parcela.isHydrated) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // IA Report Section
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF00FF88).copy(0.08f)).border(1.dp, Color(0xFF00FF88).copy(0.2f), RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(parcela.informeIA ?: "Generando dictamen...", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                            Text("DATOS SIGPAC", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("USO", parcela.sigpacInfo?.usoSigpac ?: "-", false)
                            DataField("SUP", "${parcela.sigpacInfo?.superficie ?: "-"} ha", false)
                            DataField("PEND", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %", false)
                        }
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                            Text("SOLICITUD PAC", color = Color(0xFF62D2FF), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("PROD", parcela.cultivoInfo?.producto?.toString() ?: "-", false)
                            DataField("AYUDA", parcela.cultivoInfo?.ayudaSol ?: "-", false)
                            DataField("SIST", parcela.cultivoInfo?.sistExp ?: "-", false)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline.copy(0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text("LOCALIZAR EN VISOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String, isLoading: Boolean) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        if (isLoading) {
            Box(modifier = Modifier.width(50.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(0.05f)))
        } else {
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
