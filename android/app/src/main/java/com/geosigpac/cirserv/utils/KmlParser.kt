
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

                // 1. Extraer ExtendedData (Soporte para Data y SimpleData)
                val extendedDataList = element.getElementsByTagName("ExtendedData")
                if (extendedDataList.length > 0) {
                    val extendedElement = extendedDataList.item(0) as Element
                    
                    // A) Data (Formato estándar)
                    val dataNodes = extendedElement.getElementsByTagName("Data")
                    for (j in 0 until dataNodes.length) {
                        val dataElem = dataNodes.item(j) as Element
                        val name = dataElem.getAttribute("name")
                        val value = dataElem.getElementsByTagName("value").item(0)?.textContent?.trim() ?: ""
                        metadata[name] = value
                    }

                    // B) SchemaData -> SimpleData (Formato OGR/QGIS como el ejemplo del usuario)
                    val schemaDataList = extendedElement.getElementsByTagName("SchemaData")
                    for (k in 0 until schemaDataList.length) {
                        val schemaElement = schemaDataList.item(k) as Element
                        val simpleDataNodes = schemaElement.getElementsByTagName("SimpleData")
                        for (l in 0 until simpleDataNodes.length) {
                            val simpleElem = simpleDataNodes.item(l) as Element
                            val name = simpleElem.getAttribute("name")
                            val value = simpleElem.textContent?.trim() ?: ""
                            metadata[name] = value
                        }
                    }
                }

                // 2. Extraer Geometría (Polygon preferido, Point fallback) y calcular Centroide
                var latCentroid = 0.0
                var lngCentroid = 0.0
                var geometryJson: String? = null

                // Intentar buscar Polygon
                val polygonNodes = element.getElementsByTagName("Polygon")
                if (polygonNodes.length > 0) {
                    val polygon = polygonNodes.item(0) as Element
                    val outerBoundary = polygon.getElementsByTagName("outerBoundaryIs").item(0) as? Element
                    val linearRing = outerBoundary?.getElementsByTagName("LinearRing")?.item(0) as? Element
                    val coordsNode = linearRing?.getElementsByTagName("coordinates")?.item(0)

                    if (coordsNode != null) {
                        val coordsText = coordsNode.textContent.trim()
                        val points = parseCoordinates(coordsText)
                        
                        if (points.isNotEmpty()) {
                            // Calcular Centroide simple
                            var sumLat = 0.0
                            var sumLng = 0.0
                            points.forEach {
                                sumLng += it.first
                                sumLat += it.second
                            }
                            latCentroid = sumLat / points.size
                            lngCentroid = sumLng / points.size

                            // Construir GeoJSON Polygon String
                            val coordsString = points.joinToString(",") { "[${it.first},${it.second}]" }
                            geometryJson = "{ \"type\": \"Polygon\", \"coordinates\": [[$coordsString]] }"
                        }
                    }
                } 

                if (geometryJson == null) {
                    val pointNodes = element.getElementsByTagName("Point")
                    if (pointNodes.length > 0) {
                        val coordsNode = (pointNodes.item(0) as Element).getElementsByTagName("coordinates").item(0)
                        if (coordsNode != null) {
                            val rawCoords = coordsNode.textContent.trim().split(",")
                            if (rawCoords.size >= 2) {
                                lngCentroid = rawCoords[0].toDoubleOrNull() ?: 0.0
                                latCentroid = rawCoords[1].toDoubleOrNull() ?: 0.0
                                geometryJson = "{ \"type\": \"Point\", \"coordinates\": [$lngCentroid, $latCentroid] }"
                            }
                        }
                    }
                }

                // 3. LOGICA DE REFERENCIA SIGPAC ESTRICTA (5 PARTES)
                var refSigPac = ""
                
                // Opción A: Buscar atributo ref_sigpac (case insensitive)
                val rawRef = getAttributeCaseInsensitive(metadata, "ref_sigpac") 
                             ?: getAttributeCaseInsensitive(metadata, "referencia")
                
                if (rawRef != null) {
                    // Limpieza y validación de partes
                    val parts = rawRef.trim().split(Regex("[.\\-_\\s:]+"))
                    if (parts.size >= 7) {
                        // Si tiene 7 partes (P:M:Ag:Zo:Pol:Par:Rec), quitamos Ag y Zo (indices 2 y 3)
                        refSigPac = "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
                    } else if (parts.size >= 5) {
                        // Asumimos formato correcto P:M:Pol:Par:Rec
                        refSigPac = "${parts[0]}:${parts[1]}:${parts[2]}:${parts[3]}:${parts[4]}"
                    } else {
                        // Fallback: usar como está pero normalizado
                        refSigPac = rawRef.trim().replace(Regex("[.\\-_\\s]"), ":")
                    }
                } else {
                    // Opción B: Construir desde atributos individuales (EXCLUYENDO Agregado y Zona)
                    val p = getAttributeCaseInsensitive(metadata, "provincia")
                    val m = getAttributeCaseInsensitive(metadata, "municipio")
                    val pol = getAttributeCaseInsensitive(metadata, "poligono")
                    val parc = getAttributeCaseInsensitive(metadata, "parcela")
                    val rec = getAttributeCaseInsensitive(metadata, "recinto")
                    
                    if (p != null && m != null && pol != null && parc != null && rec != null) {
                        // Forzamos formato 5 partes estrictamente: P:M:Pol:Par:Rec
                        refSigPac = "$p:$m:$pol:$parc:$rec"
                    } else {
                        // Opción C: Fallback final al nombre del elemento KML
                        val name = element.getElementsByTagName("name").item(0)?.textContent?.trim() ?: "RECINTO_$i"
                        refSigPac = name.replace(".", "")
                    }
                }

                // Obtener Uso y Superficie
                val uso = getAttributeCaseInsensitive(metadata, "uso_sigpac") 
                          ?: getAttributeCaseInsensitive(metadata, "uso") ?: "N/D"
                val superficie = getAttributeCaseInsensitive(metadata, "dn_surface")?.toDoubleOrNull() 
                                 ?: getAttributeCaseInsensitive(metadata, "superficie")?.toDoubleOrNull() ?: 0.0

                if (latCentroid != 0.0 || lngCentroid != 0.0) {
                    parcelas.add(
                        NativeParcela(
                            id = "p_${System.currentTimeMillis()}_$i",
                            referencia = refSigPac,
                            uso = uso,
                            lat = latCentroid,
                            lng = lngCentroid,
                            geometry = geometryJson,
                            area = superficie,
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

    private fun getAttributeCaseInsensitive(map: Map<String, String>, key: String): String? {
        return map.entries.find { it.key.equals(key, ignoreCase = true) }?.value
    }

    private fun parseCoordinates(text: String): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        val tuples = text.trim().split("\\s+".toRegex())
        for (tuple in tuples) {
            val parts = tuple.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lon != null && lat != null) {
                    list.add(Pair(lon, lat))
                }
            }
        }
        return list
    }
}
