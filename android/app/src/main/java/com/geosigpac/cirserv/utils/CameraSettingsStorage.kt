
package com.geosigpac.cirserv.utils

import android.content.Context
import androidx.camera.core.ImageCapture
import com.geosigpac.cirserv.ui.camera.CameraQuality
import com.geosigpac.cirserv.ui.camera.GridMode
import com.geosigpac.cirserv.ui.camera.OverlayOption
import com.geosigpac.cirserv.ui.camera.PhotoFormat
import com.google.gson.Gson

data class CameraSettings(
    val photoFormat: PhotoFormat = PhotoFormat.RATIO_4_3,
    val flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val gridMode: GridMode = GridMode.OFF,
    val cameraQuality: CameraQuality = CameraQuality.MAX,
    val overlayOptions: Set<OverlayOption> = setOf(OverlayOption.DATE, OverlayOption.COORDS, OverlayOption.REF, OverlayOption.PROJECT)
)

object CameraSettingsStorage {
    private const val PREFS_NAME = "geosigpac_camera_settings"
    private const val KEY_SETTINGS = "settings_json"
    private val gson = Gson()

    fun saveSettings(context: Context, settings: CameraSettings) {
        val json = gson.toJson(settings)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETTINGS, json)
            .apply()
    }

    fun loadSettings(context: Context): CameraSettings {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETTINGS, null) ?: return CameraSettings()
        
        return try {
            gson.fromJson(json, CameraSettings::class.java)
        } catch (e: Exception) {
            CameraSettings()
        }
    }
}
