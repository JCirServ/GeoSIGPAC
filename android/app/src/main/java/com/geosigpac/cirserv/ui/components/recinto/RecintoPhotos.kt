
package com.geosigpac.cirserv.ui.components.recinto

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun RecintoPhotos(
    photos: List<String>,
    onCamera: () -> Unit,
    onGalleryOpen: (Int) -> Unit
) {
    if (photos.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(photos) { index, uriStr ->
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = "Foto evidencia",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp))
                        .clickable { onGalleryOpen(index) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                .clickable { onCamera() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Text("Añadir fotos requeridas", color = Color.Gray, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
