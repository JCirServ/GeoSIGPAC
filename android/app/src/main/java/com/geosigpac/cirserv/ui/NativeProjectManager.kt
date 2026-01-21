
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
    var selectedExpediente by remember { mutableStateOf<NativeExpediente?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = KmlParser.getFileName(context, it) ?: "Nuevo Proyecto"
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
            }
        }
    }

    if (selectedExpediente != null) {
        ProjectDetailsScreen(selectedExpediente!!, { selectedExpediente = null }, onNavigateToMap, onOpenCamera)
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121418))) {
            CenterAlignedTopAppBar(
                title = { Text("GESTIÓN TÉCNICA", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Box(
                modifier = Modifier.padding(20.dp).fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B1E23), Color(0xFF121418))))
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("IMPORTAR CARTOGRAFÍA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(expedientes) { exp ->
                    ProjectListItem(exp, { selectedExpediente = exp }, { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) })
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(exp: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    val hydrated = exp.parcelas.count { it.isHydrated }
    val progress = if (exp.parcelas.isEmpty()) 0f else hydrated.toFloat() / exp.parcelas.size

    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E23)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(Color(0xFF00FF88).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF00FF88), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray.copy(0.5f)) }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = Color(0xFF00FF88),
                trackColor = Color.White.copy(0.03f)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ANÁLISIS OGC", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("$hydrated/${exp.parcelas.size} SINCRONIZADOS", color = Color(0xFF00FF88), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(exp: NativeExpediente, onBack: () -> Unit, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    Scaffold(
        containerColor = Color(0xFF121418),
        topBar = {
            TopAppBar(
                title = { Text(exp.titular, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121418))
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(exp.parcelas) { parcela ->
                NativeRecintoCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun NativeRecintoCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) } 
    var isLoading by remember { mutableStateOf(!parcela.isHydrated) }

    LaunchedEffect(parcela.referencia) {
        if (!parcela.isHydrated) {
            isLoading = true
            val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcela.referencia)
            parcela.sigpacInfo = sigpac
            parcela.cultivoInfo = cultivo
            parcela.informeIA = GeminiService.analyzeParcela(parcela)
            parcela.isHydrated = true
            isLoading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E23).copy(alpha = 0.8f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if(parcela.isHydrated) Color(0xFF00FF88).copy(0.1f) else Color.White.copy(0.05f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if(parcela.isHydrated) Color(0xFF00FF88) else Color.Yellow))
                Spacer(Modifier.width(12.dp))
                Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00FF88))
                IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.CameraAlt, null, tint = Color.Gray) }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // IA Report Section
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF00FF88).copy(0.05f)).border(1.dp, Color(0xFF00FF88).copy(0.1f), RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(parcela.informeIA ?: "Generando análisis técnico...", color = Color(0xFF00FF88), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                            Text("SIGPAC", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("USO", parcela.sigpacInfo?.usoSigpac ?: "-", isLoading)
                            DataField("SUP", "${parcela.sigpacInfo?.superficie ?: "-"} ha", isLoading)
                            DataField("PEND", "${parcela.sigpacInfo?.pendienteMedia ?: "-"} %", isLoading)
                        }
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 10.dp), color = Color.White.copy(0.05f))
                        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                            Text("CULTIVO", color = Color(0xFF22D3EE), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            DataField("PRODUCTO", parcela.cultivoInfo?.producto?.toString() ?: "-", isLoading)
                            DataField("AYUDA", parcela.cultivoInfo?.ayudaSol ?: "-", isLoading)
                            DataField("SISTEMA", parcela.cultivoInfo?.sistExp ?: "-", isLoading)
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LOCALIZAR EN VISOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String, isLoading: Boolean) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        if (isLoading) {
            Box(modifier = Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.05f)))
        } else {
            Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
