package com.geosigpac.cirserv.utils

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource
import java.io.File

object MapCacheManager {
    
    fun setupCache(context: Context) {
        // Configurar tamaño máximo de caché a 500MB
        val cacheDir = File(context.cacheDir, "maplibre-tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        FileSource.getInstance(context).apply {
            setDiskCacheMaximumSize(500 * 1024 * 1024L) // 500MB
        }
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