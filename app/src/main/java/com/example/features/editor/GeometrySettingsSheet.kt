package com.example.features.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.GeometrySettings
import com.example.core.model.PaperSpec
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeometrySettingsSheet(
  paper: PaperSpec,
  showGrid: Boolean,
  snapSettings: SnapSettingsState,
  geometrySettings: GeometrySettings,
  onToggleShowGrid: () -> Unit,
  onUpdateGridSpacing: (Double) -> Unit,
  onUpdateSnapSettings: ((SnapSettingsState) -> SnapSettingsState) -> Unit,
  onUpdateGeometrySettings: ((GeometrySettings) -> GeometrySettings) -> Unit,
  onSplitAllIntersections: () -> Unit,
  onMergeNearbyVertices: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Geometry & Snapping Settings",
        style = MaterialTheme.typography.headlineSmall,
      )

      // Grid & Display
      Text("Grid & Visual Display", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Show Grid", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = showGrid, onCheckedChange = { onToggleShowGrid() })
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(5.0, 10.0, 12.5, 25.0, 50.0).forEach { spacing ->
          val label = if (spacing % 1.0 == 0.0) "${spacing.toInt()} mm" else "$spacing mm"
          FilterChip(
            selected = paper.gridSpacingMm == spacing,
            onClick = { onUpdateGridSpacing(spacing) },
            label = { Text(label) },
          )
        }
      }
      SettingsSwitchRow(
        title = "Construction Line Visibility",
        checked = geometrySettings.showConstructionLines,
        onCheckedChange = { checked ->
          onUpdateGeometrySettings { it.copy(showConstructionLines = checked) }
        },
      )
      SettingsSwitchRow(
        title = "Highlight Computed Intersections",
        checked = geometrySettings.showIntersections,
        onCheckedChange = { checked ->
          onUpdateGeometrySettings { it.copy(showIntersections = checked) }
        },
      )

      HorizontalDivider()

      // Snapping Controls
      Text("Precision Snapping", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Text(
        text = "Snap Distance: ${geometrySettings.snapDistancePx.roundToInt()} px",
        style = MaterialTheme.typography.bodyMedium,
      )
      Slider(
        value = geometrySettings.snapDistancePx.toFloat(),
        onValueChange = { value ->
          onUpdateGeometrySettings { it.copy(snapDistancePx = value.toDouble()) }
        },
        valueRange = 8f..44f,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SnapChip("Grid", snapSettings.snapToGrid) {
          onUpdateSnapSettings { it.copy(snapToGrid = !it.snapToGrid) }
        }
        SnapChip("Vertices", snapSettings.snapToVertex) {
          onUpdateSnapSettings { it.copy(snapToVertex = !it.snapToVertex) }
        }
        SnapChip("Endpoints", snapSettings.snapToEndpoint) {
          onUpdateSnapSettings { it.copy(snapToEndpoint = !it.snapToEndpoint) }
        }
        SnapChip("Intersections", snapSettings.snapToIntersection) {
          onUpdateSnapSettings { it.copy(snapToIntersection = !it.snapToIntersection) }
        }
        SnapChip("Midpoints", snapSettings.snapToMidpoint) {
          onUpdateSnapSettings { it.copy(snapToMidpoint = !it.snapToMidpoint) }
        }
        SnapChip("Perpendicular", snapSettings.snapToPerpendicular) {
          onUpdateSnapSettings { it.copy(snapToPerpendicular = !it.snapToPerpendicular) }
        }
        SnapChip("Centers", snapSettings.snapToCenter) {
          onUpdateSnapSettings { it.copy(snapToCenter = !it.snapToCenter) }
        }
        SnapChip("Nearest Edge", snapSettings.snapToNearestEdge) {
          onUpdateSnapSettings { it.copy(snapToNearestEdge = !it.snapToNearestEdge) }
        }
      }

      HorizontalDivider()

      // Angle Snapping & Precision
      Text("Angle Snap & Numerical Tolerance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      SettingsSwitchRow(
        title = "Angle Snapping (${geometrySettings.angleSnapStepDeg}°)",
        checked = geometrySettings.angleSnapEnabled,
        onCheckedChange = { checked ->
          onUpdateGeometrySettings { it.copy(angleSnapEnabled = checked) }
        },
      )
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(15.0, 22.5, 30.0, 45.0, 90.0).forEach { step ->
          FilterChip(
            selected = geometrySettings.angleSnapStepDeg == step,
            onClick = { onUpdateGeometrySettings { it.copy(angleSnapStepDeg = step) } },
            label = { Text("${step}°") },
          )
        }
      }

      Text("Coordinate Precision (Decimals)", style = MaterialTheme.typography.bodyMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3).forEach { prec ->
          FilterChip(
            selected = geometrySettings.coordinatePrecision == prec,
            onClick = { onUpdateGeometrySettings { it.copy(coordinatePrecision = prec) } },
            label = { Text("$prec dec") },
          )
        }
      }

      Text("Geometry Tolerance (mm)", style = MaterialTheme.typography.bodyMedium)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(0.001, 0.01, 0.1, 0.5).forEach { tol ->
          FilterChip(
            selected = geometrySettings.geometryToleranceMm == tol,
            onClick = { onUpdateGeometrySettings { it.copy(geometryToleranceMm = tol) } },
            label = { Text("$tol mm") },
          )
        }
      }

      HorizontalDivider()

      // Batch Geometry Cleanup Tools
      Text("Crease Pattern Cleanup", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = {
            onSplitAllIntersections()
            onDismiss()
          },
          modifier = Modifier.weight(1f).testTag("split_all_intersections_button"),
        ) {
          Icon(Icons.Filled.CallSplit, contentDescription = null)
          Text(" Split Intersections")
        }
        OutlinedButton(
          onClick = {
            onMergeNearbyVertices()
            onDismiss()
          },
          modifier = Modifier.weight(1f).testTag("merge_vertices_button"),
        ) {
          Icon(Icons.Filled.MergeType, contentDescription = null)
          Text(" Merge Vertices")
        }
      }
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun SnapChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
  )
}
