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
import com.geosigpac.cirserv.services.GeminiService
import com.geosigpac.cirserv.utils.KmlParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeProjectManager(
    onNavigateToMap: (Double, Double) -> Unit,
    onOpenCamera: (String) -> Unit
) {
    val context = LocalContext.current
    var expedientes by remember { mutableStateOf(listOf<NativeExpediente>()) }
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
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                expedientes = listOf(newExp) + expedientes
            }
        }
    }

    val bgDark = Color(0xFF07080D)

    if (selectedExpediente != null) {
        NativeProjectDetailsScreen(
            expediente = selectedExpediente!!,
            onBack = { selectedExpediente = null },
            onLocate = onNavigateToMap,
            onCamera = onOpenCamera
        )
    } else {
        Scaffold(
            containerColor = bgDark,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("MIS INSPECCIONES", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 16.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgDark)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Import Card
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(2.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(56.dp).background(Color(0xFF5C60F5).copy(0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FileUpload, null, tint = Color(0xFF5C60F5), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Importar KML / KMZ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Los datos se guardarán localmente", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(expedientes) { exp ->
                        ExpedienteListItem(
                            expediente = exp,
                            onSelect = { selectedExpediente = exp },
                            onDelete = { expedientes = expedientes.filter { it.id != exp.id } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpedienteListItem(expediente: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(Color(0xFF5C60F5), CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expediente.titular, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("${expediente.parcelas.size} recintos • ${expediente.fechaImportacion}", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(0.4f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeProjectDetailsScreen(
    expediente: NativeExpediente,
    onBack: () -> Unit,
    onLocate: (Double, Double) -> Unit,
    onCamera: (String) -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF07080D),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(expediente.titular, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${expediente.parcelas.size} Recintos encontrados", color = Color.Gray, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07080D))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(expediente.parcelas) { parcela ->
                RecintoExpandableCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun RecintoExpandableCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var aiChecking by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C202D)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Place, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.Gray
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Tabla de metadatos ExtendedData
                    parcela.metadata.forEach { (key, value) ->
                        if (value.isNotBlank() && key != "Ref_SigPac" && key != "descripción") {
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    key.replace("_", " ").uppercase(), 
                                    color = Color.Gray, 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    modifier = Modifier.width(110.dp)
                                )
                                Text(value, color = Color.White.copy(0.9f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (aiResult != null) {
                        Spacer(Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF00FF88).copy(0.1f)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(aiResult!!, color = Color(0xFF00FF88), fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onLocate(parcela.lat, parcela.lng) },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C60F5)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("IR AL MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        
                        Button(
                            onClick = { 
                                aiChecking = true
                                scope.launch {
                                    aiResult = GeminiService.analyzeParcela(parcela)
                                    aiChecking = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !aiChecking
                        ) { 
                            if (aiChecking) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("IA CHECK", fontSize = 11.sp, fontWeight = FontWeight.Bold) 
                        }

                        IconButton(
                            onClick = { onCamera(parcela.id) },
                            modifier = Modifier.size(42.dp).background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF161A26)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("USO SIGPAC: ${parcela.uso}", color = Color(0xFF00FF88), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${parcela.area.takeIf { it > 0 }?.let { String.format("%.4f ha", it) } ?: ""}", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}
