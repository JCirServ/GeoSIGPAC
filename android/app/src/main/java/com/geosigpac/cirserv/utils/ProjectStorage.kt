
package com.geosigpac.cirserv.utils

import android.content.Context
import com.geosigpac.cirserv.model.NativeExpediente
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ProjectStorage {
    private const val PREFS_NAME = "geosigpac_native_storage"
    private const val KEY_EXPEDIENTES = "expedientes_json"
    private val gson = Gson()

    /**
     * Guarda la lista completa de expedientes en SharedPreferences como JSON.
     */
    fun saveExpedientes(context: Context, expedientes: List<NativeExpediente>) {
        val json = gson.toJson(expedientes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXPEDIENTES, json)
            .apply()
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
            e.printStackTrace()
            emptyList()
        }
    }
}
