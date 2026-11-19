package com.example.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onNavigateBack: () -> Unit,
) {
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
          Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.titleSmall,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = prefs.themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
                label = { Text(mode.label) },
                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
              )
            }
          }
        }

        SettingsSectionCard(title = stringResource(R.string.settings_defaults)) {
          Text(
            text = "Default Grid Spacing",
            style = MaterialTheme.typography.titleSmall,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10.0, 12.5, 25.0, 50.0).forEach { spacing ->
              val label = if (spacing % 1.0 == 0.0) "${spacing.toInt()} mm" else "$spacing mm"
              FilterChip(
                selected = prefs.defaultGridSpacingMm == spacing,
                onClick = { viewModel.setDefaultGridSpacing(spacing) },
                label = { Text(label) },
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

          SettingsToggleRow(
            title = "Show Grid by Default",
            subtitle = "Display paper-space grid lines when opening projects",
            checked = prefs.defaultShowGrid,
            onCheckedChange = viewModel::setDefaultShowGrid,
          )
          SettingsToggleRow(
            title = "Snap to Grid",
            subtitle = "Snap drawn points and creases to grid intersections",
            checked = prefs.defaultSnapToGrid,
            onCheckedChange = viewModel::setDefaultSnapToGrid,
          )
          SettingsToggleRow(
            title = "Snap to Existing Vertices",
            subtitle = "Snap to nearby crease endpoints and nodes",
            checked = prefs.defaultSnapToVertex,
            onCheckedChange = viewModel::setDefaultSnapToVertex,
          )
          SettingsToggleRow(
            title = "Snap to Geometric Intersections",
            subtitle = "Snap directly to line, segment, and circle intersections",
            checked = prefs.defaultSnapToIntersection,
            onCheckedChange = viewModel::setDefaultSnapToIntersection,
          )
          SettingsToggleRow(
            title = "Snap to Edge Midpoints",
            subtitle = "Snap to the exact midpoint of crease segments",
            checked = prefs.defaultSnapToMidpoint,
            onCheckedChange = viewModel::setDefaultSnapToMidpoint,
          )
          SettingsToggleRow(
            title = "Show Construction Geometry",
            subtitle = "Display construction lines, circles, and arcs in the editor",
            checked = prefs.showConstructionLines,
            onCheckedChange = viewModel::setShowConstructionLines,
          )
          SettingsToggleRow(
            title = "Highlight Intersections",
            subtitle = "Render intersection markers across crossing geometry",
            checked = prefs.showIntersections,
            onCheckedChange = viewModel::setShowIntersections,
          )
          SettingsToggleRow(
            title = "Coordinate & Zoom HUD",
            subtitle = "Show paper-space coordinates and zoom level overlay",
            checked = prefs.showCoordinateHud,
            onCheckedChange = viewModel::setShowCoordinateHud,
          )
        }

        SettingsSectionCard(title = stringResource(R.string.settings_about)) {
          Text(
            text = "ATP Origami Studio · Version 0.2",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text =
              "Precision local-first origami crease-pattern studio with computational geometry constructions, intersection solver, multi-mode snapping, symmetry, and transformations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
