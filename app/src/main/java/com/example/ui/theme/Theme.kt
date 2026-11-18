package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.model.FoldAssignment
import com.example.data.preferences.ThemeMode

@Immutable
data class DraftingCanvasColors(
  val backdrop: Color,
  val paperSheet: Color,
  val paperShadow: Color,
  val gridMajor: Color,
  val gridMinor: Color,
  val mountain: Color,
  val valley: Color,
  val boundary: Color,
  val unassigned: Color,
  val construction: Color,
  val selection: Color,
  val snapVertex: Color,
  val snapGrid: Color,
  val vertexDot: Color,
  val hudBackground: Color,
) {
  fun colorFor(assignment: FoldAssignment): Color =
    when (assignment) {
      FoldAssignment.MOUNTAIN -> mountain
      FoldAssignment.VALLEY -> valley
      FoldAssignment.BOUNDARY -> boundary
      FoldAssignment.UNASSIGNED -> unassigned
      FoldAssignment.CONSTRUCTION -> construction
    }
}

val LocalDraftingColors =
  staticCompositionLocalOf {
    DraftingCanvasColors(
      backdrop = Color(0xFFE4E8F0),
      paperSheet = Color(0xFFFFFFFF),
      paperShadow = Color(0x220F172A),
      gridMajor = Color(0xFFCBD5E1),
      gridMinor = Color(0xFFE8EEF5),
      mountain = MountainRedLight,
      valley = ValleyBlueLight,
      boundary = BoundaryInkLight,
      unassigned = UnassignedGreyLight,
      construction = ConstructionAshLight,
      selection = SelectionAmber,
      snapVertex = SnapEmerald,
      snapGrid = SnapCyan,
      vertexDot = Color(0xFF1E293B),
      hudBackground = Color(0xE6FFFFFF),
    )
  }

private val LightCanvasColors =
  DraftingCanvasColors(
    backdrop = Color(0xFFE4E8F0),
    paperSheet = Color(0xFFFFFFFF),
    paperShadow = Color(0x220F172A),
    gridMajor = Color(0xFFCBD5E1),
    gridMinor = Color(0xFFEDF2F7),
    mountain = MountainRedLight,
    valley = ValleyBlueLight,
    boundary = BoundaryInkLight,
    unassigned = UnassignedGreyLight,
    construction = ConstructionAshLight,
    selection = SelectionAmber,
    snapVertex = SnapEmerald,
    snapGrid = SnapCyan,
    vertexDot = Color(0xFF0F172A),
    hudBackground = Color(0xEBFFFFFF),
  )

private val DarkCanvasColors =
  DraftingCanvasColors(
    backdrop = Color(0xFF090D14),
    paperSheet = Color(0xFF161F2E),
    paperShadow = Color(0x55000000),
    gridMajor = Color(0xFF2B3A52),
    gridMinor = Color(0xFF1F2B3D),
    mountain = MountainRedDark,
    valley = ValleyBlueDark,
    boundary = BoundaryInkDark,
    unassigned = UnassignedGreyDark,
    construction = ConstructionAshDark,
    selection = SelectionAmber,
    snapVertex = SnapEmerald,
    snapGrid = SnapCyan,
    vertexDot = Color(0xFFE2E8F0),
    hudBackground = Color(0xEB151C28),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BlueprintPrimaryLight,
    onPrimary = BlueprintOnPrimaryLight,
    primaryContainer = BlueprintPrimaryContainerLight,
    onPrimaryContainer = BlueprintOnPrimaryContainerLight,
    secondary = OrigamiSecondaryLight,
    onSecondary = OrigamiOnSecondaryLight,
    secondaryContainer = OrigamiSecondaryContainerLight,
    onSecondaryContainer = OrigamiOnSecondaryContainerLight,
    tertiary = DraftingTertiaryLight,
    onTertiary = DraftingOnTertiaryLight,
    tertiaryContainer = DraftingTertiaryContainerLight,
    onTertiaryContainer = DraftingOnTertiaryContainerLight,
    background = StudioBackgroundLight,
    onBackground = StudioOnSurfaceLight,
    surface = StudioSurfaceLight,
    onSurface = StudioOnSurfaceLight,
    surfaceVariant = StudioSurfaceVariantLight,
    onSurfaceVariant = StudioOnSurfaceVariantLight,
    outline = StudioOutlineLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = BlueprintPrimaryDark,
    onPrimary = BlueprintOnPrimaryDark,
    primaryContainer = BlueprintPrimaryContainerDark,
    onPrimaryContainer = BlueprintOnPrimaryContainerDark,
    secondary = OrigamiSecondaryDark,
    onSecondary = OrigamiOnSecondaryDark,
    secondaryContainer = OrigamiSecondaryContainerDark,
    onSecondaryContainer = OrigamiOnSecondaryContainerDark,
    tertiary = DraftingTertiaryDark,
    onTertiary = DraftingOnTertiaryDark,
    tertiaryContainer = DraftingTertiaryContainerDark,
    onTertiaryContainer = DraftingOnTertiaryContainerDark,
    background = StudioBackgroundDark,
    onBackground = StudioOnSurfaceDark,
    surface = StudioSurfaceDark,
    onSurface = StudioOnSurfaceDark,
    surfaceVariant = StudioSurfaceVariantDark,
    onSurfaceVariant = StudioOnSurfaceVariantDark,
    outline = StudioOutlineDark,
  )

private val StudioShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun AtpOrigamiStudioTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val useDark =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
  val canvasColors = if (useDark) DarkCanvasColors else LightCanvasColors

  CompositionLocalProvider(LocalDraftingColors provides canvasColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = StudioShapes,
      content = content,
    )
  }
}
