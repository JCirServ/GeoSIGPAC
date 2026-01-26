
package com.geosigpac.cirserv.utils

import android.content.Context
import com.geosigpac.cirserv.ui.BaseMap
import com.google.gson.Gson

data class MapSettings(
    val baseMapName: String = BaseMap.PNOA.name,
    val showRecinto: Boolean = true,
    val showCultivo: Boolean = true,
    val isInfoSheetEnabled: Boolean = true
) {
    fun getBaseMapEnum(): BaseMap {
        return try {
            BaseMap.valueOf(baseMapName)
        } catch (e: Exception) {
            BaseMap.PNOA
        }
    }
}

object MapSettingsStorage {
    private const val PREFS_NAME = "geosigpac_map_settings"
    private const val KEY_SETTINGS = "map_config_json"
    private val gson = Gson()

    fun saveSettings(context: Context, settings: MapSettings) {
        val json = gson.toJson(settings)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETTINGS, json)
            .apply()
    }

    fun loadSettings(context: Context): MapSettings {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETTINGS, null) ?: return MapSettings()
        
        return try {
            gson.fromJson(json, MapSettings::class.java)
        } catch (e: Exception) {
            MapSettings()
        }
    }
}
