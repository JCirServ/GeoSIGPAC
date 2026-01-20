package com.geosigpac.cirserv.utils

import android.content.Context
import android.net.Uri
import com.geosigpac.cirserv.model.NativeParcela
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object KmlParser {

    fun parseUri(context: Context, uri: Uri): List<NativeParcela> {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        
        return if (uri.toString().endsWith(".kmz", true)) {
            parseKmz(inputStream)
        } else {
            parseKml(inputStream)
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

                // Parse ExtendedData
                val extendedDataList = element.getElementsByTagName("ExtendedData")
                if (extendedDataList.length > 0) {
                    val dataNodes = (extendedDataList.item(0) as Element).getElementsByTagName("Data")
                    for (j in 0 until dataNodes.length) {
                        val dataElem = dataNodes.item(j) as Element
                        val name = dataElem.getAttribute("name")
                        val value = dataElem.getElementsByTagName("value").item(0)?.textContent ?: ""
                        metadata[name] = value
                    }
                }

                // Get Coordinates (Point or Polygon)
                var lat = 0.0
                var lng = 0.0
                val coordsNodes = element.getElementsByTagName("coordinates")
                if (coordsNodes.length > 0) {
                    val rawCoords = coordsNodes.item(0).textContent.trim().split("\\s+".toRegex())
                    val firstPoint = rawCoords[0].split(",")
                    if (firstPoint.size >= 2) {
                        lng = firstPoint[0].toDoubleOrNull() ?: 0.0
                        lat = firstPoint[1].toDoubleOrNull() ?: 0.0
                    }
                }

                val refSigPac = metadata["Ref_SigPac"]
                if (refSigPac != null) {
                    parcelas.add(
                        NativeParcela(
                            id = "p_$i",
                            referencia = refSigPac,
                            uso = metadata["USO_SIGPAC"] ?: "N/A",
                            lat = lat,
                            lng = lng,
                            area = metadata["DN_SURFACE"]?.toDoubleOrNull() ?: 0.0,
                            metadata = metadata
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parcelas
    }
}