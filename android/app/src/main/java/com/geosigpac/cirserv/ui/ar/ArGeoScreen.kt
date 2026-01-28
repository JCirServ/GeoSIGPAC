
package com.geosigpac.cirserv.ui.ar

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.geosigpac.cirserv.model.NativeParcela
import com.geosigpac.cirserv.ui.camera.NeonGreen
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberArCameraNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun ArGeoScreen(
    parcelas: List<NativeParcela>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var arSceneView: ArSceneView? by remember { mutableStateOf(null) }
    
    // Estado de la UI
    var statusMessage by remember { mutableStateOf("Iniciando AR Geospatial...") }
    var accuracy by remember { mutableStateOf(0.0) }
    var isGeospatialAvailable by remember { mutableStateOf(false) }
    var areLinesRendered by remember { mutableStateOf(false) }
    
    // Estructuras de AR
    val anchorNodes = remember { mutableListOf<ArNode>() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ArSceneView(ctx).apply {
                    arSceneView = this
                    
                    // Configuración Geospatial
                    configureSession { session, config ->
                        config.geospatialMode = Config.GeospatialMode.ENABLED
                        config.focusMode = Config.FocusMode.AUTO
                    }

                    onSessionResumed = { session ->
                        // Verificar soporte al reanudar
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
                        
                        if (accuracy <= 5.0 && !areLinesRendered) {
                            statusMessage = "Precisión Óptima. Renderizando Lindes..."
                            areLinesRendered = true
                            
                            // Lanzar renderizado en coroutine para no bloquear el hilo de UI/GL
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

        // UI Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            // Status Card
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
                            progress = { (1.0 - (accuracy / 25.0)).coerceIn(0.0, 1.0).toFloat() },
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
        
        // Botón Reset (Por si el usuario se mueve mucho y quiere redibujar)
        IconButton(
            onClick = { areLinesRendered = false; anchorNodes.forEach { it.detachAnchor() }; anchorNodes.clear(); arSceneView?.childNodes?.clear() },
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
 * Función compleja para dibujar líneas entre coordenadas Lat/Lng en AR.
 */
suspend fun drawParcelLines(sceneView: ArSceneView, parcelas: List<NativeParcela>) = withContext(Dispatchers.Main) {
    val earth = sceneView.session?.earth ?: return@withContext
    
    // Material de línea (Neon Green)
    val materialLoader = sceneView.materialLoader
    val colorMaterial = materialLoader.createColorInstance(io.github.sceneview.math.Color(0.0f, 1.0f, 0.53f, 1.0f)) // #00FF88

    parcelas.forEach { parcela ->
        val rawCoords = parcela.geometryRaw ?: return@forEach
        val points = parseGeometryToPoints(rawCoords)
        
        if (points.size < 2) return@forEach

        // Convertimos cada punto geográfico en un Anchor de ARCore
        val anchors = points.mapNotNull { (lat, lng) ->
            // Usamos altitud relativa al WGS84 o altitud del dispositivo - 1.5m (suelo aprox)
            // Para mayor precisión, earth.resolveAnchorOnTerrainAsync es ideal, pero asíncrono.
            // Aquí usamos una aproximación rápida: altitud de cámara - 1.5m para pegarlo al "suelo" relativo.
            val cameraAlt = earth.cameraGeospatialPose.altitude
            
            // Creamos el anchor. rotation (0,0,0,1) identity
            try {
                earth.createAnchor(lat, lng, cameraAlt - 1.5, 0f, 0f, 0f, 1f)
            } catch (e: Exception) {
                null
            }
        }

        // Dibujar líneas entre anclajes consecutivos
        for (i in 0 until anchors.size) {
            val startAnchor = anchors[i]
            val endAnchor = anchors[(i + 1) % anchors.size] // Cerrar polígono

            val startNode = ArNode(sceneView.engine).apply {
                anchor = startAnchor
                parent = sceneView
            }
            
            val endNode = ArNode(sceneView.engine).apply {
                anchor = endAnchor
                parent = sceneView
            }

            // Calculamos vector dirección y distancia en espacio de mundo AR
            // Nota: Los anchors tardan unos frames en resolverse correctamente.
            // Para dibujar la línea, creamos un nodo hijo en startNode que se estira hacia endNode.
            // Como SceneView maneja jerarquías, es más fácil calcular la línea en espacio local si ambos tienen mundo.
            // Simplificación: Dibujar cilindros en cada vértice no es suficiente, necesitamos conectar.
            
            // TÉCNICA: "Line between two nodes"
            drawLineBetweenNodes(sceneView, startNode, endNode, colorMaterial)
        }
    }
}

fun drawLineBetweenNodes(sceneView: ArSceneView, nodeA: ArNode, nodeB: ArNode, material: com.google.android.filament.MaterialInstance) {
    // Esta función debe ejecutarse en frame update para que las líneas sigan a los anclajes si estos se corrigen
    // Pero SceneView nodes son estáticos relativos a su padre.
    // Creamos un nodo intermedio que se actualiza.
    
    val lineNode = CylinderNode(
        engine = sceneView.engine,
        radius = 0.05f, // 5cm de grosor
        height = 1.0f,
        materialInstance = material
    )
    
    // Añadimos un listener para actualizar la posición/rotación/escala de la línea
    // cada vez que el frame se actualiza, ya que los Geospatial Anchors "flotan" hasta estabilizarse.
    sceneView.addChild(lineNode)
    
    lineNode.onFrame = { _ ->
        val posA = nodeA.worldPosition
        val posB = nodeB.worldPosition
        
        val direction = posB - posA
        val distance = java.lang.Math.sqrt((direction.x * direction.x + direction.y * direction.y + direction.z * direction.z).toDouble()).toFloat()
        
        if (distance > 0) {
            // Posicionar en el punto medio
            lineNode.worldPosition = (posA + posB) / 2.0f
            
            // Escalar la altura del cilindro (eje Y local por defecto en CylinderNode, o Z según config)
            // SceneView Cylinder suele estar en Y.
            lineNode.worldScale = Scale(1f, distance, 1f)
            
            // Rotar para mirar hacia B desde A
            // LookAt configura el eje -Z hacia el target. El cilindro está en Y. Necesitamos ajustar.
            lineNode.lookAt(posB, Position(0f, 1f, 0f))
            // Rotar 90 grados en X para alinear el cilindro Y con el vector Z de lookAt
            val currentRot = lineNode.worldQuaternion
            val correction = io.github.sceneview.math.Quaternion.fromAxisAngle(io.github.sceneview.math.Float3(1f, 0f, 0f), 90f)
            lineNode.worldQuaternion = currentRot * correction
        }
    }
}

// Helper rápido para parsear la geometría cruda (copiado simplificado de KmlParser)
fun parseGeometryToPoints(raw: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    try {
        // Asumiendo formato simple "lng,lat lng,lat" o GeoJSON básico
        // Limpiamos caracteres JSON si los hay
        val clean = raw.replace("[", "").replace("]", "").replace("{", "").replace("}", "")
            .replace("type", "").replace("Polygon", "").replace("coordinates", "").replace(":", "").replace("\"", "")
        
        val parts = clean.split(",").filter { it.isNotBlank() }
        
        // GeoJSON suele ser [lng, lat], [lng, lat]
        // Si viene del KML raw string: "lng,lat lng,lat"
        
        if (raw.contains("coordinates")) {
            // GeoJSON flatten logic
            for (i in 0 until parts.size step 2) {
                if (i+1 < parts.size) {
                    val lng = parts[i].trim().toDoubleOrNull()
                    val lat = parts[i+1].trim().toDoubleOrNull()
                    if (lat != null && lng != null) points.add(lat to lng)
                }
            }
        } else {
            // Space separated tuples
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
    } catch (e: Exception) {
        Log.e("ArGeo", "Error parsing geometry", e)
    }
    return points
}
