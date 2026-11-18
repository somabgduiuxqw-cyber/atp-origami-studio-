package com.example.features.projects

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.data.model.OrigamiTemplateId
import com.example.data.model.TemplateCatalog

private data class PaperPreset(
  val label: String,
  val widthMm: Double,
  val heightMm: Double,
)

private val PaperPresets =
  listOf(
    PaperPreset("200 × 200 mm", 200.0, 200.0),
    PaperPreset("150 × 150 mm", 150.0, 150.0),
    PaperPreset("300 × 300 mm", 300.0, 300.0),
    PaperPreset("297 × 210 (A4)", 297.0, 210.0),
  )

private val GridPresets = listOf(10.0, 12.5, 25.0, 50.0)

@Composable
fun NewProjectDialog(
  initialTemplateId: OrigamiTemplateId,
  defaultGridSpacingMm: Double,
  onDismiss: () -> Unit,
  onConfirm: (name: String, widthMm: Double, heightMm: Double, gridSpacingMm: Double, templateId: OrigamiTemplateId) -> Unit,
) {
  val initialTemplate =
    remember(initialTemplateId) {
      TemplateCatalog.templates.firstOrNull { it.id == initialTemplateId }
    }
  var name by remember {
    mutableStateOf(
      if (initialTemplateId == OrigamiTemplateId.BLANK) "Untitled Pattern"
      else initialTemplate?.title ?: "New Pattern"
    )
  }
  var selectedTemplateId by remember { mutableStateOf(initialTemplateId) }
  var widthText by remember { mutableStateOf("200") }
  var heightText by remember { mutableStateOf("200") }
  var gridSpacing by remember { mutableStateOf(defaultGridSpacingMm) }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.dialog_new_project_title),
        style = MaterialTheme.typography.headlineSmall,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            errorText = null
          },
          label = { Text(stringResource(R.string.dialog_project_name)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
        )

        Text(
          text = stringResource(R.string.home_templates),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TemplateCatalog.templates.forEach { template ->
            FilterChip(
              selected = selectedTemplateId == template.id,
              onClick = { selectedTemplateId = template.id },
              label = { Text(template.title) },
            )
          }
        }

        Text(
          text = stringResource(R.string.dialog_paper_preset),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PaperPresets.forEach { preset ->
            val isSelected =
              widthText.toDoubleOrNull() == preset.widthMm &&
                heightText.toDoubleOrNull() == preset.heightMm
            FilterChip(
              selected = isSelected,
              onClick = {
                widthText = preset.widthMm.toInt().toString()
                heightText = preset.heightMm.toInt().toString()
                errorText = null
              },
              label = { Text(preset.label) },
            )
          }
        }

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
            modifier = Modifier.weight(1f).testTag("new_project_width_input"),
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
            modifier = Modifier.weight(1f).testTag("new_project_height_input"),
          )
        }

        Text(
          text = stringResource(R.string.dialog_grid_spacing_mm),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GridPresets.forEach { step ->
            val label = if (step % 1.0 == 0.0) "${step.toInt()} mm" else "$step mm"
            FilterChip(
              selected = gridSpacing == step,
              onClick = { gridSpacing = step },
              label = { Text(label) },
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
          if (w == null || h == null || w < 10.0 || h < 10.0 || w > 2000.0 || h > 2000.0) {
            errorText = "Enter paper dimensions between 10 and 2000 mm."
            return@Button
          }
          onConfirm(
            name.trim().ifEmpty { "Untitled Pattern" },
            w,
            h,
            gridSpacing,
            selectedTemplateId,
          )
        },
        modifier = Modifier.testTag("confirm_create_project_button"),
      ) {
        Text(stringResource(R.string.action_create))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun RenameProjectDialog(
  currentName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember(currentName) { mutableStateOf(currentName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_rename_title)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.dialog_project_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("rename_project_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("confirm_rename_button"),
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
fun DeleteProjectDialog(
  projectName: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_delete_title)) },
    text = {
      Text(
        text = "\"$projectName\" — ${stringResource(R.string.dialog_delete_message)}",
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        modifier = Modifier.testTag("confirm_delete_button"),
      ) {
        Text(stringResource(R.string.action_delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}
