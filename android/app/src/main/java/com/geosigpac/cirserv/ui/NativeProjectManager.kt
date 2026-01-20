
package com.geosigpac.cirserv.ui

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
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
    expedientes: List<NativeExpediente>,
    onAddExpediente: (NativeExpediente) -> Unit,
    onDeleteExpediente: (String) -> Unit,
    onNavigateToMap: (Double?, Double?) -> Unit,
    onOpenCamera: (String?) -> Unit
) {
    val context = LocalContext.current
    var selectedExpediente by remember { mutableStateOf<NativeExpediente?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            val parcelas = KmlParser.parseUri(context, it)
            if (parcelas.isNotEmpty()) {
                val fileName = KmlParser.getFileName(context, it) ?: "Nuevo Proyecto"
                val newExp = NativeExpediente(
                    id = UUID.randomUUID().toString(),
                    titular = fileName.replace(".kml", "", true).replace(".kmz", "", true),
                    fechaImportacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    parcelas = parcelas
                )
                onAddExpediente(newExp)
                Toast.makeText(context, "Importados ${parcelas.size} recintos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No se encontraron datos en el archivo (KML vacío o inválido)", Toast.LENGTH_LONG).show()
            }
            isProcessing = false
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
                // ÁREA DE IMPORTACIÓN
                Box(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                        .clickable(enabled = !isProcessing) { 
                            // Intentamos abrir específicamente KML/KMZ
                            filePickerLauncher.launch(arrayOf("*/*")) 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = neonBlue, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Procesando archivo...", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).background(neonBlue.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = neonBlue, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Cargar KML o KMZ", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Toca para seleccionar archivo de inspección", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    "PROYECTOS RECIENTES",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(exp