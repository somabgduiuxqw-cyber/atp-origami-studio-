package com.example.features.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.OrigamiTemplate
import com.example.data.model.OrigamiTemplateId
import com.example.data.repositories.ProjectSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onOpenProject: (String) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showNewProjectDialog by remember { mutableStateOf<OrigamiTemplateId?>(null) }
  var projectToRename by remember { mutableStateOf<ProjectSummary?>(null) }
  var projectToDelete by remember { mutableStateOf<ProjectSummary?>(null) }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showNewProjectDialog = OrigamiTemplateId.BLANK },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.home_create_project)) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("create_project_button"),
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      HomeHeaderBar(onOpenSettings = onOpenSettings)

      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::updateSearchQuery,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (state.searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.updateSearchQuery("") },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Filled.Clear, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("project_search_input"),
      )

      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 310.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = stringResource(R.string.home_templates),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(state.templates, key = { it.id.name }) { template ->
                TemplateCard(
                  template = template,
                  onClick = { showNewProjectDialog = template.id },
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.home_recent_projects),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "${state.projects.size} total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (!state.isLoading && state.projects.isEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyProjectsState(
              isSearchActive = state.searchQuery.isNotBlank(),
              onCreateBlank = { showNewProjectDialog = OrigamiTemplateId.BLANK },
            )
          }
        } else {
          items(state.projects, key = { it.id }) { project ->
            ProjectCard(
              project = project,
              onOpen = { onOpenProject(project.id) },
              onRename = { projectToRename = project },
              onDuplicate = { viewModel.duplicateProject(project.id) },
              onDelete = { projectToDelete = project },
            )
          }
        }
      }
    }
  }

  val templateForDialog = showNewProjectDialog
  if (templateForDialog != null) {
    NewProjectDialog(
      initialTemplateId = templateForDialog,
      defaultGridSpacingMm = state.defaultGridSpacingMm,
      onDismiss = { showNewProjectDialog = null },
      onConfirm = { name, widthMm, heightMm, gridMm, templateId ->
        showNewProjectDialog = null
        viewModel.createProject(
          name = name,
          widthMm = widthMm,
          heightMm = heightMm,
          gridSpacingMm = gridMm,
          templateId = templateId,
          onCreated = onOpenProject,
        )
      },
    )
  }

  val renaming = projectToRename
  if (renaming != null) {
    RenameProjectDialog(
      currentName = renaming.name,
      onDismiss = { projectToRename = null },
      onConfirm = { newName ->
        viewModel.renameProject(renaming.id, newName)
        projectToRename = null
      },
    )
  }

  val deleting = projectToDelete
  if (deleting != null) {
    DeleteProjectDialog(
      projectName = deleting.name,
      onDismiss = { projectToDelete = null },
      onConfirm = {
        viewModel.deleteProject(deleting.id)
        projectToDelete = null
      },
    )
  }
}

@Composable
private fun HomeHeaderBar(onOpenSettings: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Architecture,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Column {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.home_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    IconButton(
      onClick = onOpenSettings,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_button"),
    ) {
      Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = stringResource(R.string.home_settings),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun TemplateCard(
  template: OrigamiTemplate,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier = Modifier.width(136.dp).testTag("template_card_${template.id.name}"),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      TemplateThumbnail(
        templateId = template.id,
        modifier = Modifier.fillMaxWidth().height(84.dp),
      )
      Column {
        Text(
          text = template.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = template.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun ProjectCard(
  project: ProjectSummary,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val formattedDate =
    remember(project.updatedAt) {
      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(project.updatedAt))
    }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
        .testTag("project_card_${project.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CreaseThumbnail(
        preview = project.preview,
        modifier = Modifier.size(84.dp),
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = project.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${formatDimension(project.widthMm)} × ${formatDimension(project.heightMm)} mm",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "${project.vertexCount} vertices · ${project.edgeCount} edges",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = formattedDate,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("project_menu_${project.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Project options",
          )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onRename()
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_duplicate)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onDuplicate()
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyProjectsState(
  isSearchActive: Boolean,
  onCreateBlank: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Architecture,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp),
      )
      Text(
        text =
          if (isSearchActive) stringResource(R.string.home_no_search_results)
          else stringResource(R.string.home_empty_title),
        style = MaterialTheme.typography.titleMedium,
      )
      if (!isSearchActive) {
        Text(
          text = stringResource(R.string.home_empty_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Tap + New Project or choose a Starter Base above.",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable(onClick = onCreateBlank).padding(8.dp),
        )
      }
    }
  }
}

private fun formatDimension(value: Double): String {
  return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
}
