
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

                // 1. Extraer ExtendedData (Soporte dual: Data y SimpleData)
                val extendedDataList = element.getElementsByTagName("ExtendedData")
                if (extendedDataList.length > 0) {
                    val extendedElem = extendedDataList.item(0) as Element
                    
                    // Formato A: <Data name="..."> (Estándar Google Earth)
                    val dataNodes = extendedElem.getElementsByTagName("Data")
                    for (j in 0 until dataNodes.length) {
                        val dataElem = dataNodes.item(j) as Element
                        val name = dataElem.getAttribute("name")
                        val value = dataElem.getElementsByTagName("value").item(0)?.textContent?.trim() ?: ""
                        metadata[name.lowercase()] = value
                    }

                    // Formato B: <SchemaData ...> <SimpleData name="..."> (Formato OGC/QGIS)
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

                // 2. Extraer o Construir Referencia SIGPAC (Lógica Heurística Mejorada)
                var rawRef = ""

                // Definimos listas de posibles claves para cada atributo (prioridad de izquierda a derecha)
                val keysProv = listOf("provincia", "prov", "dn_prov", "cpro", "cd_prov", "cod_prov")
                val keysMun = listOf("municipio", "mun", "dn_mun", "cmun", "cd_mun", "cod_mun")
                val keysPol = listOf("poligono", "pol", "dn_pol", "cpol", "cd_pol", "polig")
                val keysParc = listOf("parcela", "parc", "par", "dn_par", "cpar", "cd_par")
                val keysRec = listOf("recinto", "rec", "dn_rec", "crec", "cd_rec")
                val keysAgg = listOf("agregado", "agg", "dn_agg", "cagg")
                val keysZon = listOf("zona", "zon", "dn_zon", "czona")

                fun findValue(keys: List<String>): String? {
                    // Busca el primer valor no nulo y no vacío en el mapa de metadatos
                    return keys.firstNotNullOfOrNull { key -> 
                        metadata[key]?.takeIf { it.isNotEmpty() } 
                    }
                }

                val p = findValue(keysProv)
                val m = findValue(keysMun)
                val polVal = findValue(keysPol)
                val parVal = findValue(keysParc)
                val recVal = findValue(keysRec)

                if (p != null && m != null && polVal != null && parVal != null && recVal != null) {
                    // Si tenemos los 5 componentes obligatorios, construimos la referencia completa
                    // Asumimos 0 para Agregado y Zona si no existen
                    val a = findValue(keysAgg) ?: "0"
                    val z = findValue(keysZon) ?: "0"
                    
                    rawRef = "$p:$m:$a:$z:$polVal:$parVal:$recVal"
                } else {
                    // Fallback: Buscar una referencia compuesta ya existente
                    val keysRef = listOf("ref_sigpac", "referencia", "dn_ref", "codigo", "label", "id", "name")
                    val existingRef = findValue(keysRef)
                    
                    if (existingRef != null && existingRef.contains(Regex("[:\\-]"))) {
                        // Si parece una referencia (tiene separadores), la usamos
                        rawRef = existingRef
                    } else {
                        // Último recurso: Usar el nombre del Placemark o generar ID
                        rawRef = element.getElementsByTagName("name").item(0)?.textContent?.trim() ?: "RECINTO_$i"
                    }
                }

                // 3. Formatear para visualización (Prov:Mun:Pol:Parc:Rec)
                val displayRef = formatToDisplayRef(rawRef)

                // 4. Extraer Uso (Buscando variantes)
                val keysUso = listOf("uso_sigpac", "uso", "dn_uso", "uso_sig", "desc_uso")
                val uso = findValue(keysUso) ?: "N/D"

                // 5. Extraer Superficie (Buscando variantes)
                val keysSup = listOf("dn_surface", "superficie", "area", "sup", "dn_sup", "shape_area")
                val superficieStr = findValue(keysSup) ?: "0"
                // Limpiar comas por puntos si es necesario y parsear
                val area = superficieStr.replace(",", ".").toDoubleOrNull() ?: 0.0

                // 6. Extraer Coordenadas y Calcular Centroide Local
                var centroidLat = 0.0
                var centroidLng = 0.0
                var coordsRaw: String? = null

                val coordsNodes = element.getElementsByTagName("coordinates")
                if (coordsNodes.length > 0) {
                    val coordsText = coordsNodes.item(0).textContent.trim()
                    coordsRaw = coordsText
                    
                    // Calcular centroide matemático
                    val rawCoords = coordsText.split("\\s+".toRegex())
                    var sumLat = 0.0
                    var sumLng = 0.0
                    var count = 0

                    if (rawCoords.isNotEmpty()) {
                        for (pointStr in rawCoords) {
                            val parts = pointStr.split(",")
                            if (parts.size >= 2) {
                                val lng = parts[0].toDoubleOrNull()
                                val lat = parts[1].toDoubleOrNull()
                                if (lng != null && lat != null) {
                                    sumLat += lat
                                    sumLng += lng
                                    count++
                                }
                            }
                        }
                    }
                    
                    if (count > 0) {
                        centroidLat = sumLat / count
                        centroidLng = sumLng / count
                    }
                }

                // 7. Crear objeto
                parcelas.add(
                    NativeParcela(
                        id = "p_${System.currentTimeMillis()}_$i",
                        referencia = displayRef, 
                        uso = uso,
                        lat = centroidLat, // Usamos el centroide calculado
                        lng = centroidLng, // Usamos el centroide calculado
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
     * Convierte cualquier referencia (7 partes, con guiones, etc.) 
     * al formato visual estándar de 5 partes: Prov:Mun:Pol:Parc:Rec
     */
    private fun formatToDisplayRef(raw: String): String {
        // Limpiar separadores (guiones o espacios por dos puntos)
        val clean = raw.replace("-", ":").replace(" ", ":")
        val parts = clean.split(":").filter { it.isNotBlank() }

        return when {
            // Caso completo: Prov:Mun:Agg:Zon:Pol:Parc:Rec (7 partes) -> Cogemos 0,1,4,5,6
            parts.size >= 7 -> "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
            // Caso intermedio (sin agg/zon explicito pero 5 partes): Asumimos Prov:Mun:Pol:Parc:Rec
            parts.size == 5 -> "${parts[0]}:${parts[1]}:${parts[2]}:${parts[3]}:${parts[4]}"
            // Caso ya resumido o incompleto: Devolvemos tal cual con separador ':'
            else -> parts.joinToString(":")
        }
    }
}
