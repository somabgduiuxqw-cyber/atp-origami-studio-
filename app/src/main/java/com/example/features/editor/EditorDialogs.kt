package com.example.features.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.geometry.SymmetrySpec
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import java.util.Locale

@Composable
fun PaperSettingsDialog(
  currentPaper: PaperSpec,
  onDismiss: () -> Unit,
  onApply: (widthMm: Double, heightMm: Double, gridSpacingMm: Double) -> Unit,
) {
  var widthText by remember(currentPaper) { mutableStateOf(formatCleanNumber(currentPaper.widthMm)) }
  var heightText by remember(currentPaper) { mutableStateOf(formatCleanNumber(currentPaper.heightMm)) }
  var gridText by remember(currentPaper) { mutableStateOf(formatCleanNumber(currentPaper.gridSpacingMm)) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.option_paper_settings)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = widthText,
            onValueChange = {
              widthText = it
              errorText = null
            },
            label = { Text(stringResource(R.string.dialog_paper_width_mm)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = heightText,
            onValueChange = {
              heightText = it
              errorText = null
            },
            label = { Text(stringResource(R.string.dialog_paper_height_mm)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        OutlinedTextField(
          value = gridText,
          onValueChange = {
            gridText = it
            errorText = null
          },
          label = { Text(stringResource(R.string.dialog_grid_spacing_mm)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(10.0, 12.5, 25.0, 50.0).forEach { preset ->
            FilterChip(
              selected = gridText.toDoubleOrNull() == preset,
              onClick = { gridText = formatCleanNumber(preset) },
              label = { Text(formatCleanNumber(preset)) },
            )
          }
        }

        if (errorText != null) {
          Text(
            text = errorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val w = widthText.toDoubleOrNull()
          val h = heightText.toDoubleOrNull()
          val g = gridText.toDoubleOrNull()
          if (w == null || h == null || w < 10.0 || h < 10.0 || w > 2000.0 || h > 2000.0) {
            errorText = "Paper dimensions must be between 10 and 2000 mm."
            return@Button
          }
          if (g == null || g < 1.0 || g > 500.0) {
            errorText = "Grid spacing must be between 1 and 500 mm."
            return@Button
          }
          onApply(w, h, g)
        }
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun EditVertexCoordinatesDialog(
  vertex: Vertex,
  onDismiss: () -> Unit,
  onApply: (xMm: Double, yMm: Double) -> Unit,
) {
  var xText by remember(vertex) { mutableStateOf(formatCleanNumber(vertex.position.x)) }
  var yText by remember(vertex) { mutableStateOf(formatCleanNumber(vertex.position.y)) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Vertex Coordinates (mm)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = xText,
            onValueChange = {
              xText = it
              errorText = null
            },
            label = { Text("X (mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("edit_vertex_x_input"),
          )
          OutlinedTextField(
            value = yText,
            onValueChange = {
              yText = it
              errorText = null
            },
            label = { Text("Y (mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("edit_vertex_y_input"),
          )
        }
        if (errorText != null) {
          Text(
            text = errorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val x = xText.toDoubleOrNull()
          val y = yText.toDoubleOrNull()
          if (x == null || y == null) {
            errorText = "Enter valid numeric coordinates."
            return@Button
          }
          onApply(x, y)
        },
        modifier = Modifier.testTag("confirm_vertex_coords_button"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun EditEdgePropertiesDialog(
  currentLengthMm: Double,
  currentAngleDeg: Double,
  onDismiss: () -> Unit,
  onApplyLength: (Double) -> Unit,
  onApplyAngle: (Double) -> Unit,
) {
  var lengthText by remember(currentLengthMm) { mutableStateOf(formatCleanNumber(currentLengthMm)) }
  var angleText by remember(currentAngleDeg) { mutableStateOf(formatCleanNumber(currentAngleDeg)) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Line Length & Angle") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = lengthText,
          onValueChange = {
            lengthText = it
            errorText = null
          },
          label = { Text("Length (mm)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = angleText,
          onValueChange = {
            angleText = it
            errorText = null
          },
          label = { Text("Angle (deg)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
          Text(
            text = errorText.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val len = lengthText.toDoubleOrNull()
          val ang = angleText.toDoubleOrNull()
          if (len == null || len <= 0.1 || ang == null) {
            errorText = "Enter positive length and valid angle."
            return@Button
          }
          if (kotlin.math.abs(len - currentLengthMm) > 1e-3) {
            onApplyLength(len)
          }
          if (kotlin.math.abs(ang - currentAngleDeg) > 1e-3) {
            onApplyAngle(ang)
          }
          onDismiss()
        }
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun CircleRadiusDialog(
  center: Vec2,
  onDismiss: () -> Unit,
  onConfirm: (radiusMm: Double) -> Unit,
) {
  var radiusText by remember { mutableStateOf("50") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Circle Radius") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = String.format(Locale.US, "Center: (%.1f, %.1f) mm", center.x, center.y),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = radiusText,
          onValueChange = {
            radiusText = it
            errorText = null
          },
          label = { Text("Radius (mm)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(25.0, 50.0, 75.0, 100.0).forEach { r ->
            FilterChip(
              selected = radiusText.toDoubleOrNull() == r,
              onClick = { radiusText = formatCleanNumber(r) },
              label = { Text("${r.toInt()} mm") },
            )
          }
        }
        if (errorText != null) {
          Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val r = radiusText.toDoubleOrNull()
          if (r == null || r <= 0.1 || r > 2000.0) {
            errorText = "Radius must be between 0.1 and 2000 mm."
            return@Button
          }
          onConfirm(r)
        }
      ) {
        Text(stringResource(R.string.action_create))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
fun MoveDeltaDialog(
  onDismiss: () -> Unit,
  onConfirm: (dx: Double, dy: Double, duplicate: Boolean) -> Unit,
) {
  var dxText by remember { mutableStateOf("25") }
  var dyText by remember { mutableStateOf("0") }
  var duplicate by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Translate Selection") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = dxText,
            onValueChange = { dxText = it },
            label = { Text("ΔX (mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = dyText,
            onValueChange = { dyText = it },
            label = { Text("ΔY (mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = duplicate, onCheckedChange = { duplicate = it })
          Text("Keep original (Duplicate)", style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val dx = dxText.toDoubleOrNull() ?: 0.0
          val dy = dyText.toDoubleOrNull() ?: 0.0
          onConfirm(dx, dy, duplicate)
        }
      ) { Text("Apply") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
fun RotateAngleDialog(
  onDismiss: () -> Unit,
  onConfirm: (angleDeg: Double, duplicate: Boolean) -> Unit,
) {
  var angleText by remember { mutableStateOf("45") }
  var duplicate by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rotate Selection") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = angleText,
          onValueChange = { angleText = it },
          label = { Text("Angle (degrees)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(15.0, 22.5, 45.0, 90.0, 180.0).forEach { deg ->
            FilterChip(
              selected = angleText.toDoubleOrNull() == deg,
              onClick = { angleText = formatCleanNumber(deg) },
              label = { Text("${formatCleanNumber(deg)}°") },
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = duplicate, onCheckedChange = { duplicate = it })
          Text("Keep original (Duplicate)", style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val deg = angleText.toDoubleOrNull() ?: return@Button
          onConfirm(deg, duplicate)
        }
      ) { Text("Rotate") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
fun ScaleFactorDialog(
  onDismiss: () -> Unit,
  onConfirm: (factor: Double, duplicate: Boolean) -> Unit,
) {
  var factorText by remember { mutableStateOf("1.5") }
  var duplicate by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Scale Selection") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = factorText,
          onValueChange = { factorText = it },
          label = { Text("Scale Factor") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(0.5, 0.75, 1.5, 2.0).forEach { f ->
            FilterChip(
              selected = factorText.toDoubleOrNull() == f,
              onClick = { factorText = formatCleanNumber(f) },
              label = { Text("${formatCleanNumber(f)}×") },
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = duplicate, onCheckedChange = { duplicate = it })
          Text("Keep original (Duplicate)", style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val factor = factorText.toDoubleOrNull() ?: return@Button
          if (factor > 0.01) onConfirm(factor, duplicate)
        }
      ) { Text("Scale") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
fun SymmetryDialog(
  paperCenter: Vec2,
  onDismiss: () -> Unit,
  onApplySymmetry: (SymmetrySpec) -> Unit,
  onPickCustomAxis: () -> Unit,
) {
  var rotationCount by remember { mutableIntStateOf(4) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Symmetry & Pattern Repeat") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Mirror or repeat selected geometry (or all interior creases if none selected).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onApplySymmetry(SymmetrySpec.VerticalCenter) },
            modifier = Modifier.weight(1f),
          ) { Text("Vertical |") }
          OutlinedButton(
            onClick = { onApplySymmetry(SymmetrySpec.HorizontalCenter) },
            modifier = Modifier.weight(1f),
          ) { Text("Horizontal —") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onApplySymmetry(SymmetrySpec.DiagonalMain) },
            modifier = Modifier.weight(1f),
          ) { Text("Diagonal \\") }
          OutlinedButton(
            onClick = { onApplySymmetry(SymmetrySpec.DiagonalAnti) },
            modifier = Modifier.weight(1f),
          ) { Text("Diagonal /") }
        }
        OutlinedButton(
          onClick = onPickCustomAxis,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Custom 2-Point Axis on Canvas")
        }

        Text("Rotational Symmetry Around Center", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(2, 3, 4, 6, 8, 12).forEach { count ->
            FilterChip(
              selected = rotationCount == count,
              onClick = { rotationCount = count },
              label = { Text("${count}-fold") },
            )
          }
        }
        Button(
          onClick = { onApplySymmetry(SymmetrySpec.Rotational(paperCenter, rotationCount)) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Apply $rotationCount-fold Rotation")
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

private fun formatCleanNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
  }
}
