
package com.geosigpac.cirserv.utils

import android.content.Context
import android.util.Log
import com.geosigpac.cirserv.model.NativeExpediente
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object ProjectStorage {
    private const val PREFS_NAME = "geosigpac_native_storage"
    private const val KEY_EXPEDIENTES = "expedientes_json"
    private const val TAG = "ProjectStorage"

    // FIX CRÍTICO: Habilitar serialización de valores flotantes especiales (NaN, Infinity).
    // Esto evita el crash "NaN is not a valid double value" si algún cálculo geométrico falla.
    private val gson: Gson = GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .create()

    /**
     * Guarda la lista completa de expedientes en SharedPreferences como JSON.
     */
    fun saveExpedientes(context: Context, expedientes: List<NativeExpediente>) {
        try {
            val json = gson.toJson(expedientes)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EXPEDIENTES, json)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Error guardando expedientes en disco: ${e.message}", e)
        }
    }

    /**
     * Recupera la lista de expedientes del almacenamiento persistente.
     */
    fun loadExpedientes(context: Context): List<NativeExpediente> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXPEDIENTES, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<NativeExpediente>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Error cargando/parseando expedientes: ${e.message}", e)
            emptyList()
        }
    }
}
