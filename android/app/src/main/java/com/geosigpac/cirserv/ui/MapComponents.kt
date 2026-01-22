
package com.geosigpac.cirserv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomSigpacKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FieldSurface)
            .padding(8.dp)
            .navigationBarsPadding()
    ) {
        // Cabecera del teclado
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TECLADO SIGPAC", color = FieldGray, style = MaterialTheme.typography.labelSmall, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Cerrar", tint = FieldGray)
            }
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(":", "0", "DEL")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val weight = 1f
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(60.dp) // Botones más grandes
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FieldBackground)
                            .clickable {
                                if (key == "DEL") onBackspace() else onKey(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(Icons.Default.Backspace, "Borrar", tint = HighContrastWhite)
                        } else {
                            Text(
                                text = key, 
                                style = MaterialTheme.typography.headlineMedium, 
                                fontWeight = FontWeight.Bold, 
                                color = HighContrastWhite,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        
        // Botón Buscar Gigante
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FieldGreen),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Search, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("BUSCAR PARCELA", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun AttributeItem(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FieldGray, fontSize = 13.sp)
        Text(
            text = if (value.isNullOrEmpty() || value == "null" || value == "0") "-" else value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.Bold, 
            color = HighContrastWhite,
            fontSize = 16.sp
        )
    }
}
