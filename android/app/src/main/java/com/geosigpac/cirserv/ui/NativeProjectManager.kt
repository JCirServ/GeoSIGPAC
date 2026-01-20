
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
                BottomNavigationBar(onNavigateToMap, onOpenCamera)
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Importador
                Box(
                    modifier = Modifier.padding(20.dp).fillMaxWidth().height(120.dp).clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.03f)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.UploadFile, null, tint = Color(0xFF5C60F5), modifier = Modifier.size(32.dp))
                        Text("Importar KML/KMZ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(expedientes) { exp ->
                        ProjectListItem(exp, onSelect = { selectedExpediente = exp }, onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) })
                    }
                }
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
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07080D))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Barra de progreso fotos (Simulada)
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PROGRESO FOTOS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("0%", color = Color(0xFF00FF88), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

    // Hidratación al montar
    LaunchedEffect(parcela.referencia) {
        if (!parcela.isHydrated) {
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
            // Cabecera de Tarjeta
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
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
                        Text("0/2 Fotos", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            Text("Compatible: ", color = Color(0xFF00FF88), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Cultivo compatible con uso ${parcela.uso}.", color = Color(0xFF00FF88).copy(0.8f), fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Grid de dos columnas
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        // Columna Recinto
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("RECINTO (SIGPAC)", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFFFBBF24).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            DataField("MUNICIPIO", parcela.sigpacInfo?.municipio ?: parcela.referencia.split("-", ":").getOrNull(1) ?: "-", isLoading)
                            DataField("PENDIENTE", parcela.sigpacInfo?.pendiente?.toString() ?: "-", isLoading)
                            DataField("COEF REGADIO", "100", isLoading)
                            
                            Spacer(Modifier.height(8.dp))
                            Text("INCIDENCIAS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("• 21 - Catastrado de arroz", color = Color.White.copy(0.7f), fontSize = 10.sp)
                            Text("• 196 - Visto en campo", color = Color.White.copy(0.7f), fontSize = 10.sp)
                        }

                        Spacer(Modifier.width(1.dp))

                        // Columna Declaración
                        Column(modifier = Modifier.weight(1f).background(Color(0xFF13141F)).padding(12.dp)) {
                            Text("DECLARACIÓN", color = Color(0xFF22D3EE), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Divider(color = Color(0xFF22D3EE).copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (parcela.cultivoInfo == null && !isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Warning, null, tint = Color.Gray.copy(0.3f))
                                        Text("SIN DECLARACIÓN", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                DataField("EXP NUM", parcela.cultivoInfo?.expNum ?: "-", isLoading)
                                DataField("PRODUCTO", parcela.cultivoInfo?.producto ?: "-", isLoading)
                                DataField("SIST EXP", parcela.cultivoInfo?.sistExp ?: "-", isLoading)
                                DataField("AYUDA SOL", parcela.cultivoInfo?.ayudaSol ?: "-", isLoading)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onLocate(parcela.lat, parcela.lng) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("LOCALIZAR EN MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun DataField(label: String, value: String, isLoading: Boolean) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        if (isLoading) {
            Box(modifier = Modifier.width(60.dp).height(12.dp).background(Color.White.copy(0.05f), RoundedCornerShape(4.dp)))
        } else {
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
