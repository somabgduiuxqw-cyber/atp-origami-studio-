package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

data class StudioPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val defaultGridSpacingMm: Double = 25.0,
  val defaultShowGrid: Boolean = true,
  val defaultSnapToGrid: Boolean = true,
  val defaultSnapToVertex: Boolean = true,
  val showCoordinateHud: Boolean = true,
)

private val Context.studioDataStore by preferencesDataStore(name = "atp_studio_preferences")

class StudioPreferencesRepository(private val context: Context) {
  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DEFAULT_GRID_SPACING = doublePreferencesKey("default_grid_spacing_mm")
    val DEFAULT_SHOW_GRID = booleanPreferencesKey("default_show_grid")
    val DEFAULT_SNAP_GRID = booleanPreferencesKey("default_snap_grid")
    val DEFAULT_SNAP_VERTEX = booleanPreferencesKey("default_snap_vertex")
    val SHOW_COORDINATE_HUD = booleanPreferencesKey("show_coordinate_hud")
  }

  val preferences: Flow<StudioPreferences> =
    context.studioDataStore.data.map { prefs ->
      val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
      val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
      StudioPreferences(
        themeMode = themeMode,
        defaultGridSpacingMm = prefs[Keys.DEFAULT_GRID_SPACING] ?: 25.0,
        defaultShowGrid = prefs[Keys.DEFAULT_SHOW_GRID] ?: true,
        defaultSnapToGrid = prefs[Keys.DEFAULT_SNAP_GRID] ?: true,
        defaultSnapToVertex = prefs[Keys.DEFAULT_SNAP_VERTEX] ?: true,
        showCoordinateHud = prefs[Keys.SHOW_COORDINATE_HUD] ?: true,
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setDefaultGridSpacing(spacingMm: Double) {
    context.studioDataStore.edit { it[Keys.DEFAULT_GRID_SPACING] = spacingMm }
  }

  suspend fun setDefaultShowGrid(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SHOW_GRID] = enabled }
  }

  suspend fun setDefaultSnapToGrid(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_GRID] = enabled }
  }

  suspend fun setDefaultSnapToVertex(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_VERTEX] = enabled }
  }

  suspend fun setShowCoordinateHud(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.SHOW_COORDINATE_HUD] = enabled }
  }
}
