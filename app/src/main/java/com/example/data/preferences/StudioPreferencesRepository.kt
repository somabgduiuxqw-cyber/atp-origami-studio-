package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
  val defaultSnapToEndpoint: Boolean = true,
  val defaultSnapToIntersection: Boolean = true,
  val defaultSnapToMidpoint: Boolean = true,
  val defaultSnapToPerpendicular: Boolean = true,
  val defaultSnapToCenter: Boolean = true,
  val defaultSnapToNearestEdge: Boolean = false,
  val snapDistancePx: Double = 22.0,
  val angleSnapEnabled: Boolean = false,
  val angleSnapStepDeg: Double = 22.5,
  val coordinatePrecision: Int = 1,
  val showConstructionLines: Boolean = true,
  val showIntersections: Boolean = true,
  val geometryToleranceMm: Double = 0.01,
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
    val DEFAULT_SNAP_ENDPOINT = booleanPreferencesKey("default_snap_endpoint")
    val DEFAULT_SNAP_INTERSECTION = booleanPreferencesKey("default_snap_intersection")
    val DEFAULT_SNAP_MIDPOINT = booleanPreferencesKey("default_snap_midpoint")
    val DEFAULT_SNAP_PERPENDICULAR = booleanPreferencesKey("default_snap_perpendicular")
    val DEFAULT_SNAP_CENTER = booleanPreferencesKey("default_snap_center")
    val DEFAULT_SNAP_NEAREST_EDGE = booleanPreferencesKey("default_snap_nearest_edge")
    val SNAP_DISTANCE_PX = doublePreferencesKey("snap_distance_px")
    val ANGLE_SNAP_ENABLED = booleanPreferencesKey("angle_snap_enabled")
    val ANGLE_SNAP_STEP_DEG = doublePreferencesKey("angle_snap_step_deg")
    val COORDINATE_PRECISION = intPreferencesKey("coordinate_precision")
    val SHOW_CONSTRUCTION_LINES = booleanPreferencesKey("show_construction_lines")
    val SHOW_INTERSECTIONS = booleanPreferencesKey("show_intersections")
    val GEOMETRY_TOLERANCE_MM = doublePreferencesKey("geometry_tolerance_mm")
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
        defaultSnapToEndpoint = prefs[Keys.DEFAULT_SNAP_ENDPOINT] ?: true,
        defaultSnapToIntersection = prefs[Keys.DEFAULT_SNAP_INTERSECTION] ?: true,
        defaultSnapToMidpoint = prefs[Keys.DEFAULT_SNAP_MIDPOINT] ?: true,
        defaultSnapToPerpendicular = prefs[Keys.DEFAULT_SNAP_PERPENDICULAR] ?: true,
        defaultSnapToCenter = prefs[Keys.DEFAULT_SNAP_CENTER] ?: true,
        defaultSnapToNearestEdge = prefs[Keys.DEFAULT_SNAP_NEAREST_EDGE] ?: false,
        snapDistancePx = prefs[Keys.SNAP_DISTANCE_PX] ?: 22.0,
        angleSnapEnabled = prefs[Keys.ANGLE_SNAP_ENABLED] ?: false,
        angleSnapStepDeg = prefs[Keys.ANGLE_SNAP_STEP_DEG] ?: 22.5,
        coordinatePrecision = prefs[Keys.COORDINATE_PRECISION] ?: 1,
        showConstructionLines = prefs[Keys.SHOW_CONSTRUCTION_LINES] ?: true,
        showIntersections = prefs[Keys.SHOW_INTERSECTIONS] ?: true,
        geometryToleranceMm = prefs[Keys.GEOMETRY_TOLERANCE_MM] ?: 0.01,
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

  suspend fun setDefaultSnapToEndpoint(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_ENDPOINT] = enabled }
  }

  suspend fun setDefaultSnapToIntersection(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_INTERSECTION] = enabled }
  }

  suspend fun setDefaultSnapToMidpoint(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_MIDPOINT] = enabled }
  }

  suspend fun setDefaultSnapToPerpendicular(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_PERPENDICULAR] = enabled }
  }

  suspend fun setDefaultSnapToCenter(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_CENTER] = enabled }
  }

  suspend fun setDefaultSnapToNearestEdge(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.DEFAULT_SNAP_NEAREST_EDGE] = enabled }
  }

  suspend fun setSnapDistancePx(px: Double) {
    context.studioDataStore.edit { it[Keys.SNAP_DISTANCE_PX] = px }
  }

  suspend fun setAngleSnapEnabled(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.ANGLE_SNAP_ENABLED] = enabled }
  }

  suspend fun setAngleSnapStepDeg(deg: Double) {
    context.studioDataStore.edit { it[Keys.ANGLE_SNAP_STEP_DEG] = deg }
  }

  suspend fun setCoordinatePrecision(decimals: Int) {
    context.studioDataStore.edit { it[Keys.COORDINATE_PRECISION] = decimals.coerceIn(0, 4) }
  }

  suspend fun setShowConstructionLines(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.SHOW_CONSTRUCTION_LINES] = enabled }
  }

  suspend fun setShowIntersections(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.SHOW_INTERSECTIONS] = enabled }
  }

  suspend fun setGeometryToleranceMm(tol: Double) {
    context.studioDataStore.edit { it[Keys.GEOMETRY_TOLERANCE_MM] = tol }
  }

  suspend fun setShowCoordinateHud(enabled: Boolean) {
    context.studioDataStore.edit { it[Keys.SHOW_COORDINATE_HUD] = enabled }
  }
}
