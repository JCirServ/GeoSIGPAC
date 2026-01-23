
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
import kotlin.math.max
import kotlin.math.min

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
                val keysProv = listOf("provincia", "prov", "dn_prov", "cpro", "cd_prov", "cod_prov")
                val keysMun = listOf("municipio", "mun", "dn_mun", "cmun", "cd_mun", "cod_mun")
                val keysPol = listOf("poligono", "pol", "dn_pol", "cpol", "cd_pol", "polig")
                val keysParc = listOf("parcela", "parc", "par", "dn_par", "cpar", "cd_par")
                val keysRec = listOf("recinto", "rec", "dn_rec", "crec", "cd_rec")
                val keysAgg = listOf("agregado", "agg", "dn_agg", "cagg")
                val keysZon = listOf("zona", "zon", "dn_zon", "czona")

                fun findValue(keys: List<String>): String? {
                    return keys.firstNotNullOfOrNull { key -> metadata[key]?.takeIf { it.isNotEmpty() } }
                }

                val p = findValue(keysProv)
                val m = findValue(keysMun)
                val polVal = findValue(keysPol)
                val parVal = findValue(keysParc)
                val recVal = findValue(keysRec)

                if (p != null && m != null && polVal != null && parVal != null && recVal != null) {
                    val a = findValue(keysAgg) ?: "0"
                    val z = findValue(keysZon) ?: "0"
                    rawRef = "$p:$m:$a:$z:$polVal:$parVal:$recVal"
                } else {
                    val keysRef = listOf("ref_sigpac", "referencia", "dn_ref", "codigo", "label", "id", "name")
                    val existingRef = findValue(keysRef)
                    if (existingRef != null && existingRef.contains(Regex("[:\\-]"))) {
                        rawRef = existingRef
                    } else {
                        rawRef = element.getElementsByTagName("name").item(0)?.textContent?.trim() ?: "RECINTO_$i"
                    }
                }

                // 3. Formatear y Atributos
                var displayRef = formatToDisplayRef(rawRef)
                val keysUso = listOf("uso_sigpac", "uso", "dn_uso", "uso_sig", "desc_uso")
                val uso = findValue(keysUso) ?: "N/D"
                val keysSup = listOf("dn_surface", "superficie", "area", "sup", "dn_sup", "shape_area")
                val superficieStr = findValue(keysSup) ?: "0"
                val area = superficieStr.replace(",", ".").toDoubleOrNull() ?: 0.0

                // 4. Procesar Geometría (Polygon o Point)
                var centroidLat = 0.0
                var centroidLng = 0.0
                var coordsRaw: String? = null
                var isPointGeometry = false

                // Intentar buscar Polygon
                var coordsNodes = element.getElementsByTagName("Polygon")
                if (coordsNodes.length == 0) {
                    // Si no hay Polygon, buscar Point
                    coordsNodes = element.getElementsByTagName("Point")
                    if (coordsNodes.length > 0) {
                        isPointGeometry = true
                    }
                }

                if (coordsNodes.length > 0) {
                    val coordsTag = (coordsNodes.item(0) as Element).getElementsByTagName("coordinates")
                    if (coordsTag.length > 0) {
                        val coordsText = coordsTag.item(0).textContent.trim()
                        
                        if (isPointGeometry) {
                            // Caso PUNTO: Extraer lat/lng y marcar para búsqueda remota
                            val parts = coordsText.split(",")
                            if (parts.size >= 2) {
                                val lng = parts[0].toDoubleOrNull() ?: 0.0
                                val lat = parts[1].toDoubleOrNull() ?: 0.0
                                centroidLat = lat
                                centroidLng = lng
                                coordsRaw = null // Dejamos null para que el ProjectManager sepa que debe buscar la geometría real
                                
                                // Si no tenemos una referencia válida parseada, ponemos una temporal
                                if (!displayRef.contains(":")) {
                                    displayRef = "PENDIENTE_${i}"
                                }
                            }
                        } else {
                            // Caso POLÍGONO: Lógica existente
                            coordsRaw = coordsText
                            val polygonPoints = mutableListOf<Pair<Double, Double>>()
                            val rawCoords = coordsText.split("\\s+".toRegex())
                            
                            for (pointStr in rawCoords) {
                                val parts = pointStr.split(",")
                                if (parts.size >= 2) {
                                    val lng = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    if (lng != null && lat != null) {
                                        polygonPoints.add(lat to lng)
                                    }
                                }
                            }

                            if (polygonPoints.isNotEmpty()) {
                                var sumLat = 0.0
                                var sumLng = 0.0
                                polygonPoints.forEach { 
                                    sumLat += it.first
                                    sumLng += it.second 
                                }
                                val avgLat = sumLat / polygonPoints.size
                                val avgLng = sumLng / polygonPoints.size

                                if (isPointInPolygon(avgLat, avgLng, polygonPoints)) {
                                    centroidLat = avgLat
                                    centroidLng = avgLng
                                } else {
                                    val corrected = getInternalPoint(avgLat, polygonPoints)
                                    centroidLat = corrected.first
                                    centroidLng = corrected.second
                                }
                            }
                        }
                    }
                }

                parcelas.add(
                    NativeParcela(
                        id = "p_${System.currentTimeMillis()}_$i",
                        referencia = displayRef, 
                        uso = uso,
                        lat = centroidLat,
                        lng = centroidLng,
                        area = area,
                        metadata = metadata, 
                        geometryRaw = coordsRaw,
                        centroidLat = centroidLat,
                        centroidLng = centroidLng
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parcelas
    }

    /**
     * Algoritmo Ray Casting: Devuelve true si el punto (testLat, testLng) está dentro del polígono.
     */
    private fun isPointInPolygon(testLat: Double, testLng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        
        for (i in polygon.indices) {
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]
            
            // Verifica intersección del rayo horizontal proyectado hacia la derecha
            if (((latI > testLat) != (latJ > testLat)) &&
                (testLng < (lngJ - lngI) * (testLat - latI) / (latJ - latI) + lngI)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Encuentra un punto garantizado dentro del polígono.
     * Traza una línea horizontal en refLat y encuentra el punto medio del primer segmento de intersección.
     */
    private fun getInternalPoint(refLat: Double, polygon: List<Pair<Double, Double>>): Pair<Double, Double> {
        val intersections = mutableListOf<Double>()
        var j = polygon.lastIndex
        
        // 1. Encontrar todas las intersecciones de longitud en la latitud de referencia
        for (i in polygon.indices) {
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]
            
            // Si el segmento cruza la latitud de referencia
            if ((latI > refLat) != (latJ > refLat)) {
                // Calcular longitud de intersección (Interpolación lineal)
                val intersectLng = (lngJ - lngI) * (refLat - latI) / (latJ - latI) + lngI
                intersections.add(intersectLng)
            }
            j = i
        }
        
        // 2. Ordenar intersecciones de oeste a este
        intersections.sort()
        
        // 3. Tomar el punto medio del primer par de intersecciones (Define un segmento "dentro" del polígono)
        if (intersections.size >= 2) {
            val newLng = (intersections[0] + intersections[1]) / 2.0
            return refLat to newLng
        }
        
        // Fallback extremo: Si falla la geometría (polígono inválido), devuelve el primer vértice.
        return polygon.firstOrNull() ?: (0.0 to 0.0)
    }

    private fun formatToDisplayRef(raw: String): String {
        val clean = raw.replace("-", ":").replace(" ", ":")
        val parts = clean.split(":").filter { it.isNotBlank() }

        return when {
            parts.size >= 7 -> "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
            parts.size == 5 -> "${parts[0]}:${parts[1]}:${parts[2]}:${parts[3]}:${parts[4]}"
            else -> parts.joinToString(":")
        }
    }
}
