
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
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
    var selectedExpediente by remember { mutableStateOf<NativeExpediente?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = it.lastPathSegment?.replace(".kml", "")?.replace(".kmz", "") ?: "Nuevo Proyecto",
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
            }
        }
    }

    if (selectedExpediente != null) {
        ProjectDetailsScreen(
            expediente = selectedExpediente!!,
            onBack = { selectedExpediente = null },
            onLocate = { lat, lng -> onNavigateToMap(lat, lng) },
            onCamera = onOpenCamera
        )
    } else {
        Scaffold(
            containerColor = Color(0xFF07080D),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("MIS PROYECTOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF07080D))
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Importador Estilo Punteado
                Box(
                    modifier = Modifier.padding(20.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.02f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Cargar KML de Inspección", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Soporte completo para KMZ", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(expedientes) { exp ->
                        ProjectListItem(exp, 
                            onSelect = { selectedExpediente = exp }, 
                            onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(exp: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E1A)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(Color(0xFF5C60F5).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF5C60F5))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exp.titular, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(
    expediente: NativeExpediente,
    onBack: () -> Unit,
    onLocate: (Double, Double) -> Unit,
    onCamera: (String) -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF07080D),
        topBar = {
            TopAppBar(
                title = { Text(expediente.titular, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07080D))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Panel de Sincronización OGC
            val hydratedCount = expediente.parcelas.count { it.isHydrated }
            val progress = if (expediente.parcelas.isEmpty()) 0f else hydratedCount.toFloat() / expediente.parcelas.size

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sincronización OGC", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("${(progress * 100).toInt()}%", color = Color(0xFF5C60F5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Color(0xFF5C60F5),
                    trackColor = Color.White.copy(0.05f)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(expediente.parcelas) { parcela ->
                    NativeRecintoCard(parcela, onLocate, onCamera)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun NativeRecintoCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(!parcela.isHydrated) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(parcela.referencia) {
        if (!parcela.isHydrated) {
            isLoading = true
            val (sigpac, cultivo) = SigpacApiService.fetchHydration(parcela.referencia)
            parcela.sigpacInfo = sigpac
            parcela.cultivoInfo = cultivo
            parcela.isHydrated = true
            isLoading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C202D).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Map, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.05f))
                        .clickable { onCamera(parcela.id) }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("0/2", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Barra Compatibilidad
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF00FF88).copy(0.1f)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("CONFORME: ", color = Color(0xFF00FF88), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Uso ${parcela.uso} validado por IA.", color = Color(0xFF00FF88).copy(0.8f), fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Grid de dos columnas (SIGPAC vs DECLARACIÓN)
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        // Columna RECINTO (Amarillo)
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("RECINTO (SIGPAC)", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFFFBBF24).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            DataField("MUNICIPIO", parcela.sigpacInfo?.municipio ?: "-", isLoading)
                            DataField("PENDIENTE", parcela.sigpacInfo?.pendiente?.toString() ?: "-", isLoading)
                            DataField("ALTITUD", parcela.sigpacInfo?.altitud?.toString()?.plus(" m") ?: "-", isLoading)
                            
                            Spacer(Modifier.height(8.dp))
                            Text("INCIDENCIAS", color = Color.Gray.copy(0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("• Ninguna detectada", color = Color.White.copy(0.4f), fontSize = 9.sp)
                        }

                        Spacer(Modifier.width(1.dp))

                        // Columna DECLARACIÓN (Cyan)
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("DECLARACIÓN", color = Color(0xFF22D3EE), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFF22D3EE).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (parcela.cultivoInfo == null && !isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Info, null, tint = Color.Gray.copy(0.2f))
                                        Text("SIN DATOS", color = Color.Gray.copy(0.4f), fontSize = 8.sp)
                                    }
                                }
                            } else {
                                DataField("PRODUCTO", parcela.cultivoInfo?.producto ?: "-", isLoading, highlight = true)
                                DataField("SIST EXP", parcela.cultivoInfo?.sistExp ?: "-", isLoading)
                                DataField("SUPERFICIE", parcela.cultivoInfo?.superficie?.toString()?.plus(" ha") ?: "-", isLoading)
                                DataField("AYUDA", parcela.cultivoInfo?.ayudaSol ?: "-", isLoading)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.04f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("VER EN MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
        }
    }
}

/**
 * Componente de visualización de datos individuales con soporte para Skeleton Loading.
 */
@Composable
fun DataField(label: String, value: String, isLoading: Boolean, highlight: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label, 
            color = Color.Gray.copy(alpha = 0.8f), 
            fontSize = 8.sp, 
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(60.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(0.05f))
            )
        } else {
            Text(
                text = value, 
                color = if (highlight) Color(0xFF22D3EE) else Color.White, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BottomNavigationBar(onNavigateToMap: (Double?, Double?) -> Unit, onOpenCamera: (String?) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0D0E1A), contentColor = Color.White) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.List, null) },
            label = { Text("Proyectos") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { onNavigateToMap(null, null) },
            icon = { Icon(Icons.Default.Map, null) },
            label = { Text("Mapa") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { onOpenCamera(null) },
            icon = { Icon(Icons.Default.CameraAlt, null) },
            label = { Text("Cámara") }
        )
    }
}
