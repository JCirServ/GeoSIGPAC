
package com.geosigpac.cirserv.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.geosigpac.cirserv.model.NativeParcela
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object KmlParser {

    fun parseUri(context: Context, uri: Uri): List<NativeParcela> {
        val fileName = getFileName(context, uri) ?: uri.toString()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        
        return try {
            if (fileName.endsWith(".kmz", true)) {
                parseKmz(inputStream)
            } else {
                parseKml(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        return result ?: uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }

    private fun parseKmz(inputStream: InputStream): List<NativeParcela> {
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".kml", true)) {
                return parseKml(zis)
            }
            entry = zis.nextEntry
        }
        return emptyList()
    }

    private fun parseKml(inputStream: InputStream): List<NativeParcela> {
        val parcelas = mutableListOf<NativeParcela>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            val placemarks: NodeList = doc.getElementsByTagName("Placemark")

            for (i in 0 until placemarks.length) {
                val element = placemarks.item(i) as Element
                val metadata = mutableMapOf<String, String>()

                // 1. Extraer ExtendedData
                val extendedDataList = element.getElementsByTagName("ExtendedData")
                if (extendedDataList.length > 0) {
                    val extendedElem = extendedDataList.item(0) as Element
                    
                    val dataNodes = extendedElem.getElementsByTagName("Data")
                    for (j in 0 until dataNodes.length) {
                        val dataElem = dataNodes.item(j) as Element
                        val name = dataElem.getAttribute("name")
                        val value = dataElem.getElementsByTagName("value").item(0)?.textContent?.trim() ?: ""
                        metadata[name.lowercase()] = value
                    }

                    val schemaDataNodes = extendedElem.getElementsByTagName("SchemaData")
                    for (k in 0 until schemaDataNodes.length) {
                        val simpleDataNodes = (schemaDataNodes.item(k) as Element).getElementsByTagName("SimpleData")
                        for (m in 0 until simpleDataNodes.length) {
                            val simpleElem = simpleDataNodes.item(m) as Element
                            val name = simpleElem.getAttribute("name")
                            val value = simpleElem.textContent.trim()
                            metadata[name.lowercase()] = value
                        }
                    }
                }

                // 2. Extraer o Construir Referencia SIGPAC
                var rawRef = ""
                if (metadata.containsKey("provincia") && metadata.containsKey("parcela")) {
                    val prov = metadata["provincia"] ?: "0"
                    val mun = metadata["municipio"] ?: "0"
                    val pol = metadata["poligono"] ?: "0"
                    val parc = metadata["parcela"] ?: "0"
                    val rec = metadata["recinto"] ?: "0"
                    val agg = metadata["agregado"] ?: "0" 
                    val zon = metadata["zona"] ?: "0"
                    rawRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
                } else {
                    rawRef = metadata["ref_sigpac"] ?: metadata["id"] ?: element.getElementsByTagName("name").item(0)?.textContent ?: "RECINTO_$i"
                }

                // 3. Formatear para visualización
                val displayRef = formatToDisplayRef(rawRef)

                // 4. Metadatos básicos
                val uso = metadata["uso_sigpac"] ?: metadata["uso"] ?: "N/D"
                val superficieStr = metadata["dn_surface"] ?: metadata["superficie"] ?: "0"
                val area = superficieStr.toDoubleOrNull() ?: 0.0

                // 5. GEOMETRÍA: Diferenciar Point vs Polygon
                var lat = 0.0
                var lng = 0.0
                var coordsRaw: String? = null

                // Buscamos Polígonos/LinearRing (Geometría Real)
                val polygons = element.getElementsByTagName("Polygon")
                val linearRings = element.getElementsByTagName("LinearRing")
                
                if (polygons.length > 0 || linearRings.length > 0) {
                    val coordsNodes = element.getElementsByTagName("coordinates")
                    if (coordsNodes.length > 0) {
                        val coordsText = coordsNodes.item(0).textContent.trim()
                        coordsRaw = coordsText
                        
                        // Centroide aproximado del primer punto para centrar mapa
                        val rawCoords = coordsText.split("\\s+".toRegex())
                        if (rawCoords.isNotEmpty()) {
                            val firstPoint = rawCoords[0].split(",")
                            if (firstPoint.size >= 2) {
                                lng = firstPoint[0].toDoubleOrNull() ?: 0.0
                                lat = firstPoint[1].toDoubleOrNull() ?: 0.0
                            }
                        }
                    }
                } else {
                    // Si no hay polígono, buscamos Punto (Point)
                    // En este caso, geometryRaw se queda NULL para forzar la hidratación externa
                    val points = element.getElementsByTagName("Point")
                    if (points.length > 0) {
                        val coordsNodes = element.getElementsByTagName("coordinates")
                        if (coordsNodes.length > 0) {
                            val coordsText = coordsNodes.item(0).textContent.trim()
                            val parts = coordsText.split(",")
                            if (parts.size >= 2) {
                                lng = parts[0].toDoubleOrNull() ?: 0.0
                                lat = parts[1].toDoubleOrNull() ?: 0.0
                            }
                        }
                    }
                }

                // 6. Crear objeto
                parcelas.add(
                    NativeParcela(
                        id = "p_${System.currentTimeMillis()}_$i",
                        referencia = displayRef,
                        uso = uso,
                        lat = lat,
                        lng = lng,
                        area = area,
                        metadata = metadata,
                        geometryRaw = coordsRaw // Será null si es un punto (chincheta)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parcelas
    }

    private fun formatToDisplayRef(raw: String): String {
        val clean = raw.replace("-", ":")
        val parts = clean.split(":").filter { it.isNotBlank() }

        return when {
            parts.size >= 7 -> "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
            else -> parts.joinToString(":")
        }
    }
}
