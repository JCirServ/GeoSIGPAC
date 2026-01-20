
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0D0E1A),
                    contentColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { /* Ya estamos aquí */ },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Proyectos") },
                        label = { Text("Proyectos", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00FF88),
                            selectedTextColor = Color(0xFF00FF88),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onOpenCamera(null) },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Cámara") },
                        label = { Text("Cámara", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigateToMap(null, null) },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                        label = { Text("Mapa", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(expedientes) { exp ->
                        ProjectListItem(exp, { selectedExpediente = exp }, { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) })
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

    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E1A)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFF5C60F5).copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF5C60F5), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exp.titular, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${exp.parcelas.size} recintos • ${exp.fechaImportacion}", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray) }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Color(0xFF00FF88),
                trackColor = Color.White.copy(0.05f)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SINCRO OGC", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${(progress * 100).toInt()}%", color = Color(0xFF00FF88), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(expediente.parcelas) { parcela ->
                NativeRecintoCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun NativeRecintoCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) } // Empezar colapsado para ver más recintos
    var isLoading by remember { mutableStateOf(!parcela.isHydrated) }

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
                Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onCamera(parcela.id) }) { Icon(Icons.Default.CameraAlt, null, tint = Color.Gray) }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        // Columna SIGPAC (Amarillo)
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("RECINTO SIGPAC", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFFFBBF24).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            val s = parcela.sigpacInfo
                            DataField("SUPERFICIE", "${s?.superficie ?: "-"} ha", isLoading)
                            DataField("PENDIENTE", "${s?.pendienteMedia ?: "-"} %", isLoading)
                            DataField("COEF REGADIO", s?.coefRegadio?.toString() ?: "N/D", isLoading)
                            DataField("ADMISIBILIDAD", s?.admisibilidad?.toString() ?: "N/D", isLoading)
                            DataField("USO SIGPAC", s?.usoSigpac ?: "-", isLoading)
                            DataField("REGION", s?.region ?: "-", isLoading)
                            DataField("ALTITUD", "${s?.altitud ?: "-"} m", isLoading)
                            
                            if (s?.incidencias?.isNotEmpty() == true) {
                                Spacer(Modifier.height(8.dp))
                                Text("INCIDENCIAS", color = Color.Red.copy(0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(s.incidencias ?: "", color = Color.White.copy(0.7f), fontSize = 9.sp)
                            }
                        }

                        Spacer(Modifier.width(1.dp))

                        // Columna DECLARACIÓN (Cian)
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("CULTIVO DECLARADO", color = Color(0xFF22D3EE), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFF22D3EE).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            val c = parcela.cultivoInfo
                            if (c == null && !isLoading) {
                                Text("SIN DECLARACIÓN", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(top = 20.dp))
                            } else {
                                DataField("EXP NUM", c?.expNum ?: "-", isLoading, highlight = true)
                                DataField("PRODUCTO", c?.producto?.toString() ?: "-", isLoading)
                                DataField("SIST EXP", c?.sistExp ?: "-", isLoading)
                                DataField("SUP CULT", "${c?.supCult ?: "-"} m2", isLoading)
                                DataField("AYUDA SOL", c?.ayudaSol ?: "-", isLoading)
                                DataField("PDR REC", c?.pdrRec ?: "-", isLoading)
                                DataField("CULT SEC PROD", c?.cultSecunProducto?.toString() ?: "-", isLoading)
                                DataField("CULT SEC AYUDA", c?.cultSecunAyudaSol ?: "-", isLoading)
                                DataField("IND CULT APRO", c?.indCultApro?.toString() ?: "-", isLoading)
                                DataField("TIPO APROVECHA", c?.tipoAprovecha ?: "-", isLoading)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.04f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LOCALIZAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String, isLoading: Boolean, highlight: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Color.Gray.copy(0.7f), fontSize = 7.sp, fontWeight = FontWeight.Black)
        if (isLoading) {
            Box(modifier = Modifier.width(60.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.05f)))
        } else {
            Text(value, color = if (highlight) Color(0xFF22D3EE) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
