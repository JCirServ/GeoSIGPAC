
package com.geosigpac.cirserv.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.geosigpac.cirserv.ui.components.recinto.NativeRecintoCard
import com.geosigpac.cirserv.utils.KmlParser
import com.geosigpac.cirserv.utils.ProjectStorage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeProjectManager(
    expedientes: List<NativeExpediente>,
    activeProjectId: String?,
    onUpdateExpedientes: (List<NativeExpediente>) -> Unit,
    onActivateProject: (String) -> Unit,
    onNavigateToMap: (String) -> Unit,
    onOpenCamera: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedExpedienteId by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = KmlParser.getFileName(context, it) ?: "Expediente"
            val newExpId = UUID.randomUUID().toString()
            val parcelas = KmlParser.parseUri(context, it, newExpId)
            
            if (parcelas.isNotEmpty()) {
                val newExp = NativeExpediente(
                    id = newExpId,
                    titular = fileName.substringBeforeLast("."),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onUpdateExpedientes(listOf(newExp) + expedientes)
            }
        }
    }

    if (selectedExpedienteId != null) {
        val currentExp = expedientes.find { it.id == selectedExpedienteId }
        if (currentExp != null) {
            ProjectDetailsScreen(
                exp = currentExp, 
                onBack = { selectedExpedienteId = null }, 
                onLocate = onNavigateToMap, 
                onCamera = onOpenCamera,
                onUpdateExpediente = { updatedExp ->
                    onUpdateExpedientes(expedientes.map { if (it.id == updatedExp.id) updatedExp else it })
                }
            )
        } else {
            selectedExpedienteId = null
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenterAlignedTopAppBar(
                title = { Text("ESTACIÓN DE TRABAJO", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            // --- MEJORA 2: DASHBOARD DE CAMPAÑA ---
            CampaignDashboard(expedientes)

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier.padding(16.dp).fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2D3033), Color(0xFF1A1C1E))))
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(20.dp))
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF00FF88))
                    Spacer(Modifier.width(12.dp))
                    Text("IMPORTAR CARTOGRAFÍA", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }

            Text("EXPEDIENTES ACTIVOS", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(expedientes, key = { it.id }) { exp ->
                    ProjectListItem(
                        exp = exp, 
                        isActive = exp.id == activeProjectId,
                        onActivate = { onActivateProject(exp.id) },
                        onSelect = { selectedExpedienteId = exp.id }, 
                        onDelete = { onUpdateExpedientes(expedientes.filter { it.id != exp.id }) }
                    )
                }
            }
        }
    }
}

@Composable
fun CampaignDashboard(expedientes: List<NativeExpediente>) {
    val totalRecintos = expedientes.sumOf { it.parcelas.size }
    val totalVisitados = expedientes.sumOf { it.parcelas.count { p -> p.photos.isNotEmpty() } }
    val totalConformes = expedientes.sumOf { it.parcelas.count { p -> p.finalVerdict == "CUMPLE" } }
    
    val progress = if (totalRecintos > 0) totalVisitados.toFloat() / totalRecintos else 0f

    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00FF88).copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color(0xFF00FF88).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ESTADO DE INSPECCIÓN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF00FF88))
                    Text("${(progress * 100).toInt()}% Completado", fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Icon(Icons.Default.Analytics, null, tint = Color(0xFF00FF88).copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
            }
            
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00FF88),
                trackColor = Color.White.copy(0.1f)
            )
            
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("Recintos", totalRecintos.toString())
                StatItem("Conformes", totalConformes.toString(), Color(0xFF00FF88))
                StatItem("Pendientes", (totalRecintos - totalVisitados).toString(), Color.Gray)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color = Color.White) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
    }
}

// Resto de ProjectListItem y componentes existentes...
