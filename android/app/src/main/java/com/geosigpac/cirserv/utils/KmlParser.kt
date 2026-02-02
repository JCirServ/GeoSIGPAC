
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
import java.util.UUID

object KmlParser {

    fun parseUri(context: Context, uri: Uri, parentExpId: String): List<NativeParcela> {
        val fileName = getFileName(context, uri) ?: uri.toString()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        
        return try {
            if (fileName.endsWith(".kmz", true)) {
                parseKmz(inputStream, parentExpId)
            } else {
                parseKml(inputStream, parentExpId)
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

    private fun parseKmz(inputStream: InputStream, parentExpId: String): List<NativeParcela> {
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".kml", true)) {
                return parseKml(zis, parentExpId)
            }
            entry = zis.nextEntry
        }
        return emptyList()
    }

    private fun parseKml(inputStream: InputStream, parentExpId: String): List<NativeParcela> {
        val parcelas = mutableListOf<NativeParcela>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            val placemarks: NodeList = doc.getElementsByTagName("Placemark")

            for (i in 0 until placemarks.length) {
                val element = placemarks.item(i) as Element
                val metadata = mutableMapOf<String, String>()

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
                }

                var rawRef = ""
                val keysProv = listOf("provincia", "prov")
                val keysMun = listOf("municipio", "mun")
                val keysPol = listOf("poligono", "pol")
                val keysParc = listOf("parcela", "parc")
                val keysRec = listOf("recinto", "rec")

                fun findValue(keys: List<String>): String? = keys.firstNotNullOfOrNull { metadata[it]?.takeIf { v -> v.isNotEmpty() } }

                val p = findValue(keysProv); val m = findValue(keysMun); val polVal = findValue(keysPol); val parVal = findValue(keysParc); val recVal = findValue(keysRec)

                if (p != null && m != null && polVal != null && parVal != null && recVal != null) {
                    rawRef = "$p:$m:$polVal:$parVal:$recVal"
                } else {
                    rawRef = element.getElementsByTagName("name").item(0)?.textContent?.trim() ?: "RECINTO_$i"
                }

                val displayRef = formatToDisplayRef(rawRef)
                val area = (findValue(listOf("superficie", "area")) ?: "0").replace(",", ".").toDoubleOrNull() ?: 0.0

                var centroidLat = 0.0; var centroidLng = 0.0; var coordsRaw: String? = null
                val polyNodes = element.getElementsByTagName("Polygon")
                if (polyNodes.length > 0) {
                    val coordsText = (polyNodes.item(0) as Element).getElementsByTagName("coordinates").item(0).textContent.trim()
                    coordsRaw = coordsText
                    val parts = coordsText.split("\\s+".toRegex()).firstOrNull()?.split(",")
                    if (parts != null && parts.size >= 2) {
                        centroidLng = parts[0].toDoubleOrNull() ?: 0.0
                        centroidLat = parts[1].toDoubleOrNull() ?: 0.0
                    }
                }

                parcelas.add(
                    NativeParcela(
                        id = UUID.randomUUID().toString(),
                        parentExpedienteId = parentExpId,
                        referencia = displayRef, 
                        uso = findValue(listOf("uso_sigpac", "uso")) ?: "N/D",
                        lat = centroidLat, lng = centroidLng, area = area,
                        metadata = metadata, geometryRaw = coordsRaw,
                        centroidLat = centroidLat, centroidLng = centroidLng
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return parcelas
    }

    private fun formatToDisplayRef(raw: String): String {
        val clean = raw.replace("-", ":").replace(" ", ":")
        val parts = clean.split(":").filter { it.isNotBlank() }
        return parts.joinToString(":")
    }
}
