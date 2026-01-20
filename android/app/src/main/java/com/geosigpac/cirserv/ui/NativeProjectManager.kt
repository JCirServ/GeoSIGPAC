package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.geosigpac.cirserv.utils.KmlParser
import java.text.SimpleDateFormat
import java.util.*

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
                    titular = it.lastPathSegment ?: "Nuevo Expediente",
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
                    title = { Text("Proyectos", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgDark)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Botón de Importación
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.UploadFile, null, tint = Color(0xFF5C60F5), size = 32.dp)
                        Text("Importar KML / KMZ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(expedientes) { exp ->
                        ExpedienteCard(
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
fun ExpedienteCard(expediente: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12141C)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFF5C60F5), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expediente.titular, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${expediente.parcelas.size} recintos • ${expediente.fechaImportacion}", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Gray)
            }
        }
    }
}

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
            LargeTopAppBar(
                title = { Text(expediente.titular, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color(0xFF07080D))
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            items(expediente.parcelas) { parcela ->
                RecintoCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun RecintoCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C202D)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Map, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.Gray
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    parcela.metadata.forEach { (key, value) ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(key.replace("_", " "), color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(100.dp))
                            Text(value, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onLocate(parcela.lat, parcela.lng) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C60F5))
                        ) { Text("Mapa", fontSize = 12.sp) }
                        
                        Button(
                            onClick = { onCamera(parcela.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))
                        ) { Text("Cámara", fontSize = 12.sp) }
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
                    Text("Uso: ${parcela.uso}", color = Color(0xFF00FF88), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Modifier.animateContentSize() = this.then(Modifier) // Placeholder for simplicity