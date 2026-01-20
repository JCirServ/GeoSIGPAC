
package com.geosigpac.cirserv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    val bgDark = Color(0xFF07080D)
    val neonBlue = Color(0xFF5C60F5)

    if (selectedExpediente != null) {
        ProjectDetailsScreen(
            expediente = selectedExpediente!!,
            onBack = { selectedExpediente = null },
            onLocate = { lat, lng -> onNavigateToMap(lat, lng) },
            onCamera = onOpenCamera
        )
    } else {
        Scaffold(
            containerColor = bgDark,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("MIS PROYECTOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgDark)
                )
            },
            bottomBar = {
                BottomNavigationBar(onNavigateToMap, onOpenCamera)
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                        .clickable { filePickerLauncher.launch("*/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(48.dp).background(neonBlue.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = neonBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Nuevo Proyecto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Importar archivo KML / KMZ", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Text(
                    "RECIENTES",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(expedientes) { exp ->
                        ProjectListItem(
                            expediente = exp,
                            onSelect = { selectedExpediente = exp },
                            onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                        )
                    }
                }

                if (expedientes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                        Text("No hay proyectos cargados", color = Color.White.copy(0.2f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(expediente: NativeExpediente, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFF5C60F5).copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF5C60F5), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expediente.titular, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${expediente.parcelas.size} recintos • ${expediente.fechaImportacion}", color = Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(20.dp))
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
                title = { 
                    Column {
                        Text(expediente.titular, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Campaña 2024 • ${expediente.parcelas.size} parcelas", color = Color.Gray, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
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
                RecintoDetailCard(parcela, onLocate, onCamera)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun RecintoDetailCard(parcela: NativeParcela, onLocate: (Double, Double) -> Unit, onCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiReport by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C202D)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFFF5252).copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(parcela.referencia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Uso: ${parcela.uso} • ${String.format("%.4f", parcela.area)} ha", color = Color.Gray, fontSize = 11.sp)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = Color.Gray, modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    parcela.metadata.filter { it.key != "Ref_SigPac" && it.value.isNotBlank() }.forEach { (key, value) ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(key.replace("_", " ").uppercase(), color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(90.dp))
                            Text(value, color = Color.White.copy(0.8f), fontSize = 10.sp)
                        }
                    }

                    if (aiReport != null) {
                        Spacer(Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF00FF88).copy(0.1f)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(aiReport!!, color = Color(0xFF00FF88), fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onLocate(parcela.lat, parcela.lng) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C60F5)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("MAPA", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        
                        Button(
                            onClick = { 
                                isAnalyzing = true
                                scope.launch {
                                    aiReport = GeminiService.analyzeParcela(parcela)
                                    isAnalyzing = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isAnalyzing
                        ) { 
                            if (isAnalyzing) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("IA CHECK", fontSize = 11.sp, fontWeight = FontWeight.Bold) 
                        }

                        IconButton(
                            onClick = { onCamera(parcela.id) }, 
                            modifier = Modifier.size(40.dp).background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(onNavigateToMap: (Double?, Double?) -> Unit, onOpenCamera: (String?) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF13141F),
        contentColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.Folder, null) },
            label = { Text("Proyectos", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                indicatorColor = Color(0xFF5C60F5),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { onNavigateToMap(null, null) },
            icon = { Icon(Icons.Default.Map, null) },
            label = { Text("Mapa", fontSize = 10.sp) }
        )
        NavigationBarItem(
            selected = false,
            onClick = { onOpenCamera(null) },
            icon = { Icon(Icons.Default.CameraAlt, null) },
            label = { Text("Captura", fontSize = 10.sp) }
        )
    }
}
