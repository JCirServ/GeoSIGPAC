
package com.geosigpac.cirserv.utils

import android.content.Context
import org.maplibre.android.storage.FileSource
import java.io.File

object MapCacheManager {
    
    fun setupCache(context: Context) {
        // Configurar directorio de caché
        val cacheDir = File(context.cacheDir, "maplibre-tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        // Nota: En MapLibre v11+ la gestión del tamaño de caché es automática o interna.
        // La llamada setDiskCacheMaximumSize ha sido eliminada para compatibilidad.
        // FileSource.getInstance(context).setResourceCachePath(cacheDir.absolutePath)
    }
    
    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "maplibre-tiles")
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getCacheSize(context: Context): Long {
        val cacheDir = File(context.cacheDir, "maplibre-tiles")
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
