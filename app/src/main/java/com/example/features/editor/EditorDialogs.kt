package com.example.features.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.PaperSpec
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
              label = { Text("${formatCleanNumber(preset)}") },
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

private fun formatCleanNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
  }
}
