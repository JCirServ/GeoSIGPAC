
package com.geosigpac.cirserv.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.geosigpac.cirserv.data.AppDatabase
import com.geosigpac.cirserv.data.Expediente
import com.geosigpac.cirserv.data.RecintoEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

// Modelos DTO para recibir desde JSON (React)
data class JsInspection(
    val title: String,
    val description: String,
    val date: String,
    val parcelas: List<JsParcela>
)

data class JsParcela(
    val name: String,
    val lat: Double,
    val lng: Double,
    val area: Double,
    // Coordenadas crudas simuladas o polígonos simples para GeoJSON
    val geometry: List<List<Double>>? = null 
)

class WebAppInterface(
    private val context: Context,
    private val scope: CoroutineScope, 
    private val onCameraRequested: (String) -> Unit,
    private val onMapFocusRequested: (Double, Double) -> Unit
) {
    private val dao = AppDatabase.getInstance(context).inspectionDao()

    @JavascriptInterface
    fun openCamera(projectId: String) {
        scope.launch(Dispatchers.Main) { onCameraRequested(projectId) }
    }

    @JavascriptInterface
    fun onProjectSelected(lat: Double, lng: Double) {
        scope.launch(Dispatchers.Main) { onMapFocusRequested(lat, lng) }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Recibe la inspección completa parseada desde el KML en React.
     * La guarda en la base de datos Room para acceso Offline y MapLibre.
     */
    @JavascriptInterface
    fun importInspectionData(json: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val inspection = gson.fromJson(json, JsInspection::class.java)
                
                val expId = UUID.randomUUID().toString()
                val expediente = Expediente(
                    id = expId,
                    titular = inspection.title,
                    campana = 2024,
                    fechaImportacion = System.currentTimeMillis(),
                    estadoGlobal = "pendiente"
                )
                
                val recintos = inspection.parcelas.map { p ->
                    // Simulamos la generación de un GeoJSON Point o Polygon simple
                    // En producción, React debería enviar el GeoJSON completo.
                    val geoJson = """
                        {
                            "type": "Feature",
                            "properties": { "name": "${p.name}", "declaredArea": ${p.area} },
                            "geometry": {
                                "type": "Point",
                                "coordinates": [${p.lng}, ${p.lat}]
                            }
                        }
                    """.trimIndent()

                    RecintoEntity(
                        id = UUID.randomUUID().toString(),
                        expedienteId = expId,
                        provincia = "00", // Se rellenarán con la API al visitar
                        municipio = "00",
                        poligono = "0",
                        parcela = "0",
                        recinto = "0",
                        usoDeclarado = "Declarado (KML)",
                        superficieDeclarada = p.area,
                        geomDeclaradaJson = geoJson
                    )
                }

                dao.createFullInspection(expediente, recintos)
                
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Inspección guardada offline (${recintos.size} recintos)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Error guardando datos: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
