
package com.geosigpac.cirserv.ui.ar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.ui.camera.NeonGreen
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CylinderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun ArGeoScreen(
    parcelas: List<NativeParcela>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var arSceneView: ArSceneView? by remember { mutableStateOf(null) }
    
    // Estados de UI
    var statusMessage by remember { mutableStateOf("Iniciando AR Geospatial...") }
    var accuracy by remember { mutableStateOf(0.0) }
    var areLinesRendered by remember { mutableStateOf(false) }
    
    // Función de Reset
    val onReset = {
        areLinesRendered = false
        arSceneView?.childNodes?.forEach { it.destroy() }
        arSceneView?.childNodes?.clear()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ArSceneView(ctx).apply {
                    arSceneView = this
                    
                    // Configuración de Sesión AR para Geospatial
                    configureSession { session, config ->
                        config.geospatialMode = Config.GeospatialMode.ENABLED
                        config.focusMode = Config.FocusMode.AUTO
                    }

                    onSessionFailed = { exception ->
                        Log.e("ArGeo", "Session failed", exception)
                        statusMessage = "Error AR: ${exception.message}"
                    }
                }
            },
            update = { view ->
                // Loop de actualización por frame
                view.onFrame = { frame ->
                    val earth = view.session?.earth
                    
                    if (earth?.trackingState == TrackingState.TRACKING) {
                        val pose = earth.cameraGeospatialPose
                        accuracy = pose.horizontalAccuracy
                        
                        // Umbral de precisión: 5 metros
                        if (accuracy <= 5.0 && !areLinesRendered) {
                            statusMessage = "Precisión Óptima. Renderizando Lindes..."
                            areLinesRendered = true
                            
                            scope.launch {
                                drawParcelLines(view, parcelas)
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Lindes Proyectadas"
                                }
                            }
                        } else if (!areLinesRendered) {
                            statusMessage = "Calibrando GPS: ${"%.1f".format(accuracy)}m (Req: <5m)"
                        }
                    } else {
                        statusMessage = "Esperando tracking de tierra..."
                    }
                }
            }
        )

        // Panel de Estado Superior
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.7f))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusMessage,
                        color = if(accuracy <= 5.0 && accuracy > 0) NeonGreen else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (accuracy > 0) {
                        LinearProgressIndicator(
                            progress = { (1.0 - (accuracy / 20.0)).coerceIn(0.0, 1.0).toFloat() },
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            color = NeonGreen,
                            trackColor = Color.Gray
                        )
                    }
                }
            }
        }

        // Botón Volver
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
                .size(56.dp)
                .background(Color.Black.copy(0.5f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
        }
        
        // Botón Reset (Recalibrar)
        IconButton(
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
                .size(56.dp)
                .background(NeonGreen, androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Default.GpsFixed, null, tint = Color.Black)
        }
    }
}

/**
 * Dibuja las líneas de las parcelas conectando Anchors Geoespaciales.
 */
suspend fun drawParcelLines(sceneView: ArSceneView, parcelas: List<NativeParcela>) = withContext(Dispatchers.Main) {
    val earth = sceneView.session?.earth ?: return@withContext
    val materialLoader = sceneView.materialLoader
    // Color Verde Neón (#00FF88) con ligera transparencia
    val colorMaterial = materialLoader.createColorInstance(io.github.sceneview.math.Color(0.0f, 1.0f, 0.53f, 0.9f))

    parcelas.forEach { parcela ->
        val rawCoords = parcela.geometryRaw ?: return@forEach
        val points = parseGeometryToPoints(rawCoords)
        
        if (points.size < 2) return@forEach

        // 1. Crear Anchors para cada vértice
        val anchors = points.mapNotNull { (lat, lng) ->
            // Usamos la altitud del dispositivo - 1.5m como aproximación rápida al suelo.
            // Para mayor precisión en terreno irregular, se usaría earth.resolveAnchorOnTerrainAsync
            val alt = earth.cameraGeospatialPose.altitude - 1.5
            try {
                earth.createAnchor(lat, lng, alt, 0f, 0f, 0f, 1f)
            } catch (e: Exception) { null }
        }

        // 2. Conectar Anchors con Cilindros
        for (i in 0 until anchors.size) {
            // Cerramos el polígono si es necesario
            val nextIndex = (i + 1) % anchors.size
            
            // Evitar cerrar si la lista de puntos ya repite el inicio al final (común en KML/GeoJSON)
            if (i == anchors.size - 1 && points[0] != points[points.lastIndex]) continue

            val startAnchor = anchors[i]
            val endAnchor = anchors[nextIndex]

            val startNode = ArNode(sceneView.engine).apply {
                anchor = startAnchor
                parent = sceneView
            }
            
            val endNode = ArNode(sceneView.engine).apply {
                anchor = endAnchor
                parent = sceneView
            }

            drawLineBetweenNodes(sceneView, startNode, endNode, colorMaterial)
        }
    }
}

/**
 * Crea un cilindro que se actualiza en cada frame para conectar dos nodos que pueden moverse (Anchors).
 */
fun drawLineBetweenNodes(sceneView: ArSceneView, nodeA: ArNode, nodeB: ArNode, material: com.google.android.filament.MaterialInstance) {
    val lineNode = CylinderNode(
        engine = sceneView.engine,
        radius = 0.05f, // 5cm de grosor
        height = 1.0f,
        materialInstance = material
    )
    sceneView.addChild(lineNode)
    
    // Actualización dinámica: Los Geospatial Anchors refinan su posición constantemente.
    // La línea debe redibujarse entre las nuevas posiciones A y B.
    lineNode.onFrame = {
        val posA = nodeA.worldPosition
        val posB = nodeB.worldPosition
        
        val direction = posB - posA
        val length = sqrt((direction.x * direction.x + direction.y * direction.y + direction.z * direction.z).toDouble()).toFloat()
        
        if (length > 0) {
            // Posicionar en el punto medio
            lineNode.worldPosition = (posA + posB) * 0.5f
            // Escalar longitud (Y)
            lineNode.worldScale = Scale(1f, length, 1f)
            // Apuntar al destino
            lineNode.lookAt(posB, Position(0f, 1f, 0f))
            
            // Corrección de rotación: El cilindro crece en Y, lookAt orienta -Z.
            // Rotamos 90 grados en X para alinear el cilindro con el vector de dirección.
            val correction = io.github.sceneview.math.Quaternion.fromAxisAngle(io.github.sceneview.math.Float3(1f, 0f, 0f), 90f)
            lineNode.worldQuaternion = lineNode.worldQuaternion * correction
        }
    }
}

/**
 * Parsea coordenadas crudas (GeoJSON o KML string) a lista de Pares (Lat, Lng).
 */
fun parseGeometryToPoints(raw: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    try {
        val clean = raw.replace("[", "").replace("]", "").replace("{", "").replace("}", "")
            .replace("type", "").replace("Polygon", "").replace("coordinates", "").replace(":", "").replace("\"", "")
        
        val parts = clean.split(",").filter { it.isNotBlank() }
        
        if (raw.contains("coordinates")) {
            // Formato GeoJSON: [lng, lat]
            for (i in 0 until parts.size step 2) {
                if (i+1 < parts.size) {
                    val lng = parts[i].trim().toDoubleOrNull()
                    val lat = parts[i+1].trim().toDoubleOrNull()
                    if (lat != null && lng != null) points.add(lat to lng)
                }
            }
        } else {
            // Formato String simple KML: "lng,lat lng,lat"
            val tuples = raw.trim().split("\\s+".toRegex())
            tuples.forEach { t ->
                val coords = t.split(",")
                if (coords.size >= 2) {
                    val lng = coords[0].toDoubleOrNull()
                    val lat = coords[1].toDoubleOrNull()
                    if (lat != null && lng != null) points.add(lat to lng)
                }
            }
        }
    } catch (e: Exception) { Log.e("ArGeo", "Parse error", e) }
    return points
}
