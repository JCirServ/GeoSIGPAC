
package com.geosigpac.cirserv.ui

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppThemeOption(val primary: Color, val secondary: Color, val nameStr: String) {
    CYBER_GREEN(Color(0xFF00FF88), Color(0xFF62D2FF), "Cyber Green"),
    NEON_ORANGE(Color(0xFFFF9100), Color(0xFFFF3D00), "Neon Orange"),
    ELECTRIC_BLUE(Color(0xFF2979FF), Color(0xFF00E5FF), "Electric Blue"),
    HOT_PINK(Color(0xFFFF4081), Color(0xFFF50057), "Hot Pink"),
    GOLDEN_SUN(Color(0xFFFFD740), Color(0xFFFFAB00), "Golden Sun")
}

object ThemeUtils {
    private const val PREFS_NAME = "geosigpac_theme_prefs"
    private const val KEY_THEME = "selected_theme_enum"

    fun saveTheme(context: Context, theme: AppThemeOption) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }

    fun loadTheme(context: Context): AppThemeOption {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, AppThemeOption.CYBER_GREEN.name)
        return try {
            AppThemeOption.valueOf(name ?: AppThemeOption.CYBER_GREEN.name)
        } catch (e: Exception) {
            AppThemeOption.CYBER_GREEN
        }
    }
}
