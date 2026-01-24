// utils/Logger.kt
package com.geosigpac.cirserv.utils

import android.util.Log
import com.geosigpac.cirserv.BuildConfig

object Logger {
    private const val TAG = "GeoSIGPAC"
    
    fun d(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }
    
    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        Log.e(tag, message, throwable)
    }
    
    fun i(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }
    
    fun w(message: String, tag: String = TAG) {
        Log.w(tag, message)
    }
}