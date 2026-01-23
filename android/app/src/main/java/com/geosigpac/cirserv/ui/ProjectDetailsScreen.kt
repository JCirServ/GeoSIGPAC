
package com.geosigpac.cirserv.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailsScreen(
    exp: NativeExpediente, 
    onBack: () -> Unit, 
    onLocate: (String) -> Unit, 
    onCamera: (String) -> Unit,
    onUpdateExpediente: (NativeExpediente) -> Unit
) {
    // Estado para controlar la agrupación
    var isGroupedByExpediente by remember { mutableStateOf(false) }
    
    // Mapa de estados de expansión: Key=ExpNum, Value=Boolean (true=expandido)
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(exp.titular, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isGroupedByExpediente) "Agrupado por Nº Expediente" else "Listado Completo", 
                            fontSize = 12.sp, 
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(28.dp)) } },
                actions = {
                    // Botón para alternar agrupación
                    IconButton(onClick = { isGroupedByExpediente = !isGroupedByExpediente }) {
                        Icon(
                            imageVector = if (isGroupedByExpediente) Icons.Default.AccountTree else Icons.Default.FormatListBulleted,
                            contentDescription = "Agrupar",
                            tint = if (isGroupedByExpediente) Color(0xFF00FF88) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        
        // Lógica de agrupación
        val groupedParcelas = remember(exp.parcelas, isGroupedByExpediente) {
            if (isGroupedByExpediente) {
                // Agrupamos por expNum, usando una clave especial para los vacíos
                exp.parcelas.groupBy { it.cultivoInfo?.expNum?.trim()?.ifEmpty { null } ?: "SIN_EXPEDIENTE" }
                    .toSortedMap { a, b ->
                        // Ordenar: "SIN_EXPEDIENTE" al final, el resto alfabéticamente
                        when {
                            a == "SIN_EXPEDIENTE" -> 1
                            b == "SIN_EXPEDIENTE" -> -1
                            else -> a.compareTo(b)
                        }
                    }
            } else {
                null
            }
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            
            if (isGroupedByExpediente && groupedParcelas != null) {
                groupedParcelas.forEach { (expNum, parcelasGroup) ->
                    // Recuperar estado, default true (abierto)
                    val isExpanded = expandedGroups[expNum] ?: false

                    stickyHeader {
                        ExpedienteHeader(
                            expNum = if (expNum == "SIN_EXPEDIENTE") null else expNum, 
                            count = parcelasGroup.size,
                            isExpanded = isExpanded,
                            onToggle = { expandedGroups[expNum] = !isExpanded }
                        )
                    }
                    
                    if (isExpanded) {
                        items(parcelasGroup, key = { it.id }) { parcela ->
                            NativeRecintoCard(
                                parcela = parcela, 
                                onLocate = onLocate, 
                                onCamera = onCamera,
                                onUpdateParcela = { updatedParcela ->
                                    onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }))
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            } else {
                // Vista normal sin agrupar
                items(exp.parcelas, key = { it.id }) { parcela ->
                    NativeRecintoCard(
                        parcela = parcela, 
                        onLocate = onLocate, 
                        onCamera = onCamera,
                        onUpdateParcela = { updatedParcela ->
                            onUpdateExpediente(exp.copy(parcelas = exp.parcelas.map { if (it.id == updatedParcela.id) updatedParcela else it }))
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ExpedienteHeader(
    expNum: String?, 
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val isUnassigned = expNum == null
    val NeonGreen = Color(0xFF00FF88)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onToggle() }, // Click para colapsar
        color = MaterialTheme.colorScheme.background, 
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF252525)) 
                .border(1.dp, if(isUnassigned) Color.White.copy(0.1f) else NeonGreen.copy(0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isUnassigned) Icons.Default.Warning else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (isUnassigned) Color.Gray else NeonGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isUnassigned) "SIN Nº EXPEDIENTE" else "EXP. $expNum",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isUnassigned) {
                        Text(
                            text = "Recintos no asociados a declaración",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(0.3f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$count",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = Color.White.copy(0.5f)
                )
            }
        }
    }
}
