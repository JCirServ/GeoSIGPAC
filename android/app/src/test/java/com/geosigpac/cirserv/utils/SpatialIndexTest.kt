
package com.geosigpac.cirserv.utils

import com.geosigpac.cirserv.model.NativeExpediente
import com.geosigpac.cirserv.model.NativeParcela
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpatialIndexTest {

    @Test
    fun testSpatialIndexPerformanceAndLogic() = runBlocking {
        val index = SpatialIndex()
        
        // 1. Crear datos de prueba (1000 parcelas ficticias)
        val parcelas = mutableListOf<NativeParcela>()
        for (i in 0 until 1000) {
            // Parcelas cuadradas de 0.01 grados (~1km)
            val baseLat = 39.0 + (i * 0.02)
            val baseLng = -0.5 + (i * 0.02)
            
            // GeoJSON Polygon simple
            val geom = """
                {"type":"Polygon","coordinates":[[
                    [$baseLng, $baseLat],
                    [${baseLng + 0.01}, $baseLat],
                    [${baseLng + 0.01}, ${baseLat + 0.01}],
                    [$baseLng, ${baseLat + 0.01}],
                    [$baseLng, $baseLat]
                ]]}
            """.trimIndent()
            
            parcelas.add(NativeParcela(
                id = "p$i",
                referencia = "REF_$i",
                uso = "TA",
                lat = baseLat + 0.005,
                lng = baseLng + 0.005,
                area = 1.0,
                metadata = emptyMap(),
                geometryRaw = geom
            ))
        }
        
        val exp = NativeExpediente("exp1", "Test", "2024", parcelas)
        
        // 2. Medir tiempo de construcción
        val startBuild = System.currentTimeMillis()
        index.rebuild(listOf(exp))
        val buildTime = System.currentTimeMillis() - startBuild
        println("Build Time: ${buildTime}ms")
        assertTrue("Index build should be fast", buildTime < 500)

        // 3. Test: Punto DENTRO de la parcela 0 (39.005, -0.495)
        val startSearch = System.currentTimeMillis()
        val resultInside = index.findContainingParcel(39.005, -0.495)
        val searchTime = System.currentTimeMillis() - startSearch
        
        println("Search Time: ${searchTime}ms")
        assertTrue("Search should be under 5ms", searchTime < 5)
        assertNotNull("Should find parcel", resultInside)
        assertEquals("Should be parcel p0", "p0", resultInside?.second?.id)

        // 4. Test: Punto FUERA (entre parcelas)
        val resultOutside = index.findContainingParcel(39.015, -0.485)
        assertNull("Should be null (gap)", resultOutside)
    }
}
