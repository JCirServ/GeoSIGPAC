
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

                // 2. CONSTRUCCIÓN INTELIGENTE DE REFERENCIA SIGPAC
                // Buscamos cualquier variación común de nombres de columnas
                val prov = findValue(metadata, listOf("provincia", "prov", "dn_prov", "cd_prov", "id_prov", "pro"))
                val mun = findValue(metadata, listOf("municipio", "mun", "dn_mun", "dn_municipio", "cd_mun", "id_mun"))
                val pol = findValue(metadata, listOf("poligono", "pol", "dn_pol", "cd_pol", "id_pol"))
                val parc = findValue(metadata, listOf("parcela", "parc", "par", "dn_par", "dn_parc", "id_par"))
                val rec = findValue(metadata, listOf("recinto", "rec", "dn_rec", "id_rec", "dn_recinto")) ?: "0"
                val agg = findValue(metadata, listOf("agregado", "agg", "ag", "dn_agg")) ?: "0"
                val zon = findValue(metadata, listOf("zona", "zon", "zn", "dn_zon", "dn_zona")) ?: "0"

                var rawRef = ""
                
                if (prov != null && mun != null && pol != null && parc != null) {
                    // Si tenemos los componentes críticos, construimos la referencia canónica
                    rawRef = "$prov:$mun:$agg:$zon:$pol:$parc:$rec"
                } else {
                    // Fallback: Buscar campo 'ref_sigpac' explícito o usar el Nombre/ID
                    rawRef = findValue(metadata, listOf("ref_sigpac", "referencia", "ref", "cod_parcela", "codigo")) 
                             ?: metadata["id"] 
                             ?: element.getElementsByTagName("name").item(0)?.textContent 
                             ?: "RECINTO_$i"
                }

                // 3. Formatear para visualización
                val displayRef = formatToDisplayRef(rawRef)

                // 4. Extraer Uso (Buscamos variantes)
                val uso = findValue(metadata, listOf("uso_sigpac", "uso", "dn_uso", "desc_uso")) ?: "N/D"

                // 5. Extraer Superficie
                val superficieStr = findValue(metadata, listOf("superficie", "dn_surface", "area", "superficie_ha", "ha")) ?: "0"
                val area = superficieStr.replace(",", ".").toDoubleOrNull() ?: 0.0

                // 6. Extraer Coordenadas (Geometry)
                var lat = 0.0
                var lng = 0.0
                var coordsRaw: String? = null

                val coordsNodes = element.getElementsByTagName("coordinates")
                if (coordsNodes.length > 0) {
                    val coordsText = coordsNodes.item(0).textContent.trim()
                    coordsRaw = coordsText
                    // Tomamos el primer punto para centrar
                    val rawCoords = coordsText.split("\\s+".toRegex())
                    if (rawCoords.isNotEmpty()) {
                        val firstPoint = rawCoords[0].split(",")
                        if (firstPoint.size >= 2) {
                            lng = firstPoint[0].toDoubleOrNull() ?: 0.0
                            lat = firstPoint[1].toDoubleOrNull() ?: 0.0
                        }
                    }
                }

                // 7. Crear objeto
                parcelas.add(
                    NativeParcela(
                        id = "p_${System.currentTimeMillis()}_$i",
                        referencia = displayRef,
                        uso = uso,
                        lat = lat,
                        lng = lng,
                        area = area,
                        metadata = metadata,
                        geometryRaw = coordsRaw
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parcelas
    }

    /**
     * Busca en el mapa de metadatos el primer valor no nulo para una lista de claves posibles.
     */
    private fun findValue(metadata: Map<String, String>, keys: List<String>): String? {
        for (key in keys) {
            if (metadata.containsKey(key)) {
                val value = metadata[key]
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    /**
     * Convierte cualquier referencia al formato visual estándar: Prov:Mun:Pol:Parc:Rec
     */
    private fun formatToDisplayRef(raw: String): String {
        // Limpiar separadores (guiones por dos puntos) y espacios
        val clean = raw.replace("-", ":").replace("/", ":").trim()
        val parts = clean.split(":").filter { it.isNotBlank() }

        return when {
            // Caso completo: Prov:Mun:Agg:Zon:Pol:Parc:Rec (7 partes) -> Cogemos 0,1,4,5,6
            parts.size >= 7 -> "${parts[0]}:${parts[1]}:${parts[4]}:${parts[5]}:${parts[6]}"
            // Caso sin Recinto (6 partes) o parcial: Devolvemos tal cual
            else -> parts.joinToString(":")
        }
    }
}
