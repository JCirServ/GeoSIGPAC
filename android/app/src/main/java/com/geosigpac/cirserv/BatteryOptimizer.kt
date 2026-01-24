package com.geosigpac.cirserv.utils

import android.content.Context
import android.location.LocationManager
import android.os.PowerManager

object BatteryOptimizer {
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    /**
     * Mantiene la pantalla activa durante inspección
     */
    fun acquireWakeLock(context: Context, tag: String = "GeoSIGPAC:Inspection") {
        if (wakeLock?.isHeld == true) return
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            tag
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutos máximo
        }
    }
    
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    /**
     * Ajusta intervalo GPS según nivel de batería
     */
    fun getOptimalGPSInterval(context: Context): Long {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        
        val batteryLevel = batteryManager.getIntProperty(
            android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
        )
        
        return when {
            batteryLevel > 50 -> 2000L // 2 segundos (normal)
            batteryLevel > 20 -> 5000L // 5 segundos (ahorro)
            else -> 10000L // 10 segundos (ahorro extremo)
        }
    }
}