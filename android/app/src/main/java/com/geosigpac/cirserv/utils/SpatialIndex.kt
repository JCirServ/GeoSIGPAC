
package com.geosigpac.cirserv.utils

import android.util.Log
import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import com.github.davidmoten.rtree.RTree
import com.github.davidmoten.rtree.geometry.Geometries
import com.github.davidmoten.rtree.geometry.Rectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class SpatialIndex {

    // Usamos AtomicReference para thread-safety al cambiar el árbol completo
    private val treeReference = AtomicReference<RTree<Pair<NativeExpediente, NativeParcela>, Rectangle>>(RTree.create())
    private val TAG = "SpatialIndex"

    /**
     * Reconstruye el índice R-Tree. Ejecutar en Dispatchers.Default.
     */
    suspend fun rebuild(expedientes: List<NativeExpediente>) = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        var newTree = RTree.create<Pair<NativeExpediente, NativeParcela>, Rectangle>()
        var count = 0

        expedientes.forEach { exp ->
            exp.parcelas.forEach { parcela ->
                val bounds = calculateBounds(parcela.geometryRaw)
                if (bounds != null) {
                    // RTree usa (minLng, minLat, maxLng, maxLat) -> X = Lng, Y = Lat
                    val geometry = Geometries.rectangle(
                        bounds.minLng, bounds.minLat, 
                        bounds.maxLng, bounds.maxLat
                    )
                    newTree = newTree.add(Pair(exp, parcela), geometry)
                    count++
                }
            }
        }
        
        treeReference.set(newTree)
        Log.d(TAG, "Index rebuilt with $count parcels in ${System.currentTimeMillis() - start}ms")
    }

    /**
     * Encuentra la primera parcela que contiene el punto (lat, lng).
     * 1. Búsqueda rápida por Bounding Box (R-Tree).
     * 2. Búsqueda precisa por geometría (Ray Casting).
     */
    suspend fun findContainingParcel(lat: Double, lng: Double): Pair<NativeExpediente, NativeParcela>? = withContext(Dispatchers.Default) {
        // El punto de búsqueda como una geometría pequeña o punto
        val point = Geometries.point(lng, lat)
        
        // 1. Fase R-Tree: Obtener candidatos cuyos rectángulos intersectan el punto
        // rx.Observable -> toBlocking -> toIterable para evitar complejidad reactiva innecesaria aquí
        val candidates = treeReference.get().search(point).toBlocking().toIterable()

        // 2. Fase Geometría: Comprobación precisa
        for (entry in candidates) {
            val (exp, parcela) = entry.value()
            if (isPointInParcel(lat, lng, parcela)) {
                return@withContext Pair(exp, parcela)
            }
        }
        
        return@withContext null
    }

    // --- GEOMETRY PARSING HELPERS ---

    private data class ParcelBounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

    private fun calculateBounds(geometryRaw: String?): ParcelBounds? {
        if (geometryRaw.isNullOrEmpty()) return null
        
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        var hasPoints = false

        try {
            // Estrategia híbrida: Extracción rápida de números sin parsear JSON completo si es posible,
            // o parseo ligero. Para robustez, detectamos formato.
            
            if (geometryRaw.trim().startsWith("{")) {
                // GeoJSON
                val jsonObject = JSONObject(geometryRaw)
                val type = jsonObject.optString("type")
                
                // Función recursiva para atravesar arrays de coordenadas anidados
                fun traverseCoords(arr: JSONArray) {
                    if (arr.length() == 0) return
                    val first = arr.get(0)
                    if (first is Number) { // [lng, lat]
                        val lng = arr.getDouble(0)
                        val lat = arr.getDouble(1)
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                        if (lng < minLng) minLng = lng
                        if (lng > maxLng) maxLng = lng
                        hasPoints = true
                    } else if (first is JSONArray) {
                        for (i in 0 until arr.length()) traverseCoords(arr.getJSONArray(i))
                    }
                }
                
                val coordinates = jsonObject.optJSONArray("coordinates")
                if (coordinates != null) traverseCoords(coordinates)

            } else {
                // KML Raw String ("lng,lat lng,lat ...")
                val parts = geometryRaw.trim().split("\\s+".toRegex())
                for (part in parts) {
                    val coords = part.split(",")
                    if (coords.size >= 2) {
                        val lng = coords[0].toDoubleOrNull()
                        val lat = coords[1].toDoubleOrNull()
                        if (lng != null && lat != null) {
                            if (lat < minLat) minLat = lat
                            if (lat > maxLat) maxLat = lat
                            if (lng < minLng) minLng = lng
                            if (lng > maxLng) maxLng = lng
                            hasPoints = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating bounds: ${e.message}")
            return null
        }

        return if (hasPoints) ParcelBounds(minLat, maxLat, minLng, maxLng) else null
    }

    // Algoritmo Ray Casting (Duplicado del helper original pero encapsulado aquí para seguridad)
    private fun isPointInParcel(lat: Double, lng: Double, parcela: NativeParcela): Boolean {
        val geomRaw = parcela.geometryRaw
        if (geomRaw.isNullOrEmpty()) return false
        try {
            if (geomRaw.trim().startsWith("{")) {
                val jsonObject = JSONObject(geomRaw)
                val type = jsonObject.optString("type")
                val coordinates = jsonObject.getJSONArray("coordinates")
                
                if (type.equals("Polygon", ignoreCase = true)) {
                    if (coordinates.length() > 0) return isPointInPolygonRing(lat, lng, parseJsonRing(coordinates.getJSONArray(0)))
                } else if (type.equals("MultiPolygon", ignoreCase = true)) {
                    for (i in 0 until coordinates.length()) {
                        // MultiPolygon: coordinates[i] es un Polígono, coordinates[i][0] es el anillo exterior
                        if (isPointInPolygonRing(lat, lng, parseJsonRing(coordinates.getJSONArray(i).getJSONArray(0)))) return true
                    }
                }
                return false
            } else {
                val points = mutableListOf<Pair<Double, Double>>()
                val coordPairs = geomRaw.trim().split("\\s+".toRegex())
                for (pair in coordPairs) {
                    val coords = pair.split(",")
                    if (coords.size >= 2) {
                        val pLng = coords[0].toDoubleOrNull(); val pLat = coords[1].toDoubleOrNull()
                        if (pLng != null && pLat != null) points.add(pLat to pLng)
                    }
                }
                return if (points.size >= 3) isPointInPolygonRing(lat, lng, points) else false
            }
        } catch (e: Exception) { return false }
    }

    private fun parseJsonRing(jsonRing: JSONArray): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until jsonRing.length()) {
            val pt = jsonRing.getJSONArray(i)
            list.add(pt.getDouble(1) to pt.getDouble(0))
        }
        return list
    }

    private fun isPointInPolygonRing(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]
            if (((latI > lat) != (latJ > lat)) && (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)) inside = !inside
            j = i
        }
        return inside
    }
}
