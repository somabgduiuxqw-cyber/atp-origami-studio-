package com.example.features.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.geometry.GeometryEngine
import com.example.core.model.CreasePatternDocument
import com.example.core.model.FoldAssignment
import com.example.features.projects.RenameProjectDialog
import com.example.ui.theme.LocalDraftingColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
  viewModel: EditorViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showRenameDialog by remember { mutableStateOf(false) }
  var showPaperDialog by remember { mutableStateOf(false) }
  var showEditVertexDialog by remember { mutableStateOf(false) }

  val handleExit = {
    viewModel.saveNow(onSaved = onNavigateBack)
  }

  BackHandler(onBack = handleExit)

  val doc = state.document

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = LocalDraftingColors.current.backdrop,
    topBar = {
      EditorTopToolbar(
        projectName = doc?.name ?: "Loading…",
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        showGrid = state.showGrid,
        snapToGrid = state.snapToGrid,
        snapToVertex = state.snapToVertex,
        showCoordinates = state.showCoordinates,
        onBack = handleExit,
        onRenameClick = { if (doc != null) showRenameDialog = true },
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onSave = { viewModel.saveNow() },
        onZoomToFit = viewModel::zoomToFit,
        onToggleGrid = viewModel::toggleShowGrid,
        onToggleSnapGrid = viewModel::toggleSnapToGrid,
        onToggleSnapVertex = viewModel::toggleSnapToVertex,
        onToggleCoordinates = viewModel::toggleShowCoordinates,
        onOpenPaperSettings = { if (doc != null) showPaperDialog = true },
      )
    },
    bottomBar = {
      FoldAssignmentBottomBar(
        activeAssignment = state.activeAssignment,
        onSelectAssignment = viewModel::selectAssignment,
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding)
    ) {
      when {
        state.isLoading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        state.loadError != null || doc == null -> {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
          ) {
            Text(
              text = state.loadError ?: "Unable to load project.",
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(20.dp),
            )
          }
        }
        else -> {
          CreasePatternCanvas(
            document = doc,
            state = state,
            onTransformViewport = viewModel::applyViewportTransform,
            onSnapQuery = viewModel::snapPoint,
            onHoverSnap = viewModel::updateHoverSnap,
            onClearTransientPreviews = viewModel::clearTransientPreviews,
            onPlacePoint = viewModel::placePoint,
            onBeginOrUpdateLineDrag = viewModel::beginOrUpdateLineDrag,
            onHandleLineTap = viewModel::handleLineTap,
            onCommitLineSegment = viewModel::commitLineSegment,
            onSelectAt = viewModel::selectAt,
            onFindHitVertexForDrag = viewModel::findHitVertexForDrag,
            onUpdateDragVertex = viewModel::updateDragVertexPreview,
            onCommitDragVertex = viewModel::commitDragVertexMove,
            onEraseAt = viewModel::eraseAt,
            modifier = Modifier.fillMaxSize(),
          )

          // Top-left Coordinate & Zoom HUD
          if (state.showCoordinates) {
            CoordinateHudPill(
              state = state,
              document = doc,
              modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            )
          }

          // Collapsible Left Tool Panel
          Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            AnimatedVisibility(
              visible = state.isToolPanelExpanded,
              enter = fadeIn() + slideInHorizontally { -it / 2 },
              exit = fadeOut() + slideOutHorizontally { -it / 2 },
            ) {
              EditorToolRail(
                activeTool = state.activeTool,
                onSelectTool = viewModel::selectTool,
              )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
              shadowElevation = 2.dp,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
              modifier =
                Modifier
                  .minimumInteractiveComponentSize()
                  .clickable(onClick = viewModel::toggleToolPanel)
                  .testTag("toggle_tool_panel_button"),
            ) {
              Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                Icon(
                  imageVector =
                    if (state.isToolPanelExpanded) Icons.Filled.ChevronLeft
                    else Icons.Filled.ChevronRight,
                  contentDescription = stringResource(R.string.cd_toggle_tools),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }

          // Selection Inspector Bar above bottom bar
          if (state.selection.hasSelection) {
            SelectionInspectorBar(
              document = doc,
              selection = state.selection,
              onEditVertexCoords = { showEditVertexDialog = true },
              onFlipMountainValley = viewModel::flipSelectedEdgeMountainValley,
              onDeleteSelection = viewModel::deleteSelection,
              modifier =
                Modifier
                  .align(Alignment.BottomCenter)
                  .padding(horizontal = 12.dp, vertical = 10.dp),
            )
          }
        }
      }
    }
  }

  if (showRenameDialog && doc != null) {
    RenameProjectDialog(
      currentName = doc.name,
      onDismiss = { showRenameDialog = false },
      onConfirm = { newName ->
        viewModel.renameProject(newName)
        showRenameDialog = false
      },
    )
  }

  if (showPaperDialog && doc != null) {
    PaperSettingsDialog(
      currentPaper = doc.paper,
      onDismiss = { showPaperDialog = false },
      onApply = { w, h, g ->
        viewModel.updatePaperSettings(w, h, g)
        showPaperDialog = false
      },
    )
  }

  val selectedVertex =
    if (showEditVertexDialog && doc != null && state.selection.selectedVertexId != null) {
      doc.vertexById(state.selection.selectedVertexId!!)
    } else null

  if (selectedVertex != null) {
    EditVertexCoordinatesDialog(
      vertex = selectedVertex,
      onDismiss = { showEditVertexDialog = false },
      onApply = { x, y ->
        viewModel.moveSelectedVertexTo(x, y)
        showEditVertexDialog = false
      },
    )
  }
}

@Composable
private fun EditorTopToolbar(
  projectName: String,
  canUndo: Boolean,
  canRedo: Boolean,
  isDirty: Boolean,
  isSaving: Boolean,
  showGrid: Boolean,
  snapToGrid: Boolean,
  snapToVertex: Boolean,
  showCoordinates: Boolean,
  onBack: () -> Unit,
  onRenameClick: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onSave: () -> Unit,
  onZoomToFit: () -> Unit,
  onToggleGrid: () -> Unit,
  onToggleSnapGrid: () -> Unit,
  onToggleSnapVertex: () -> Unit,
  onToggleCoordinates: () -> Unit,
  onOpenPaperSettings: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
          )
        }

        Column(
          modifier =
            Modifier
              .weight(1f)
              .clickable(onClick = onRenameClick)
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .testTag("editor_project_title"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = projectName,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Rename project",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(13.dp),
            )
          }
          Text(
            text =
              when {
                isSaving -> stringResource(R.string.status_saving)
                isDirty -> stringResource(R.string.status_unsaved)
                else -> stringResource(R.string.status_saved)
              },
            style = MaterialTheme.typography.bodySmall,
            color =
              if (isDirty) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onUndo,
          enabled = canUndo,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_undo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = stringResource(R.string.cd_undo),
          )
        }

        IconButton(
          onClick = onRedo,
          enabled = canRedo,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_redo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Redo,
            contentDescription = stringResource(R.string.cd_redo),
          )
        }

        IconButton(
          onClick = onSave,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_save_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = stringResource(R.string.cd_save),
            tint =
              if (isDirty) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_more_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.cd_more_options),
            )
          }

          DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_zoom_fit)) },
              leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onZoomToFit()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_paper_settings)) },
              leadingIcon = { Icon(Icons.Filled.GridOn, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onOpenPaperSettings()
              },
            )
            HorizontalDivider()
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_toggle_grid)) },
              trailingIcon = {
                if (showGrid) Icon(Icons.Filled.Check, contentDescription = null)
              },
              onClick = {
                onToggleGrid()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_snap_grid)) },
              trailingIcon = {
                if (snapToGrid) Icon(Icons.Filled.Check, contentDescription = null)
              },
              onClick = {
                onToggleSnapGrid()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_snap_vertex)) },
              trailingIcon = {
                if (snapToVertex) Icon(Icons.Filled.Check, contentDescription = null)
              },
              onClick = {
                onToggleSnapVertex()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_show_coords)) },
              trailingIcon = {
                if (showCoordinates) Icon(Icons.Filled.Check, contentDescription = null)
              },
              onClick = {
                onToggleCoordinates()
              },
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
  }
}

@Composable
private fun EditorToolRail(
  activeTool: EditorTool,
  onSelectTool: (EditorTool) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ToolRailItem(
        tool = EditorTool.SELECT,
        icon = Icons.Filled.NearMe,
        selected = activeTool == EditorTool.SELECT,
        onClick = { onSelectTool(EditorTool.SELECT) },
      )
      ToolRailItem(
        tool = EditorTool.PAN,
        icon = Icons.Filled.OpenWith,
        selected = activeTool == EditorTool.PAN,
        onClick = { onSelectTool(EditorTool.PAN) },
      )
      ToolRailItem(
        tool = EditorTool.POINT,
        icon = Icons.Filled.AddCircleOutline,
        selected = activeTool == EditorTool.POINT,
        onClick = { onSelectTool(EditorTool.POINT) },
      )
      ToolRailItem(
        tool = EditorTool.LINE,
        icon = Icons.Filled.Timeline,
        selected = activeTool == EditorTool.LINE,
        onClick = { onSelectTool(EditorTool.LINE) },
      )
      ToolRailItem(
        tool = EditorTool.ERASER,
        icon = Icons.Outlined.AutoFixHigh,
        selected = activeTool == EditorTool.ERASER,
        onClick = { onSelectTool(EditorTool.ERASER) },
      )
    }
  }
}

@Composable
private fun ToolRailItem(
  tool: EditorTool,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val bg =
    if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
  val fg =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = bg,
    modifier =
      Modifier
        .width(56.dp)
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .testTag("tool_${tool.name.lowercase()}"),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = tool.label,
        tint = fg,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = tool.label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun CoordinateHudPill(
  state: EditorUiState,
  document: CreasePatternDocument,
  modifier: Modifier = Modifier,
) {
  val canvasColors = LocalDraftingColors.current
  val zoomPct = (state.viewport.zoom * 100f).roundToInt()
  val pending = state.pendingLinePreview

  val hudText =
    if (pending != null && pending.currentEndPosition != null) {
      val len = GeometryEngine.distance(pending.startPosition, pending.currentEndPosition)
      val ang = GeometryEngine.angleDegrees(pending.startPosition, pending.currentEndPosition)
      String.format(Locale.US, "L: %.1f mm · %.1f° · %d%%", len, ang, zoomPct)
    } else if (state.cursorPaperPos != null) {
      String.format(
        Locale.US,
        "X: %.1f  Y: %.1f mm · %d%%",
        state.cursorPaperPos.x,
        state.cursorPaperPos.y,
        zoomPct,
      )
    } else {
      String.format(
        Locale.US,
        "%d×%d mm · %dv %de · %d%%",
        document.paper.widthMm.roundToInt(),
        document.paper.heightMm.roundToInt(),
        document.vertices.size,
        document.edges.size,
        zoomPct,
      )
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = canvasColors.hudBackground,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    shadowElevation = 2.dp,
    modifier = modifier.testTag("coordinate_hud"),
  ) {
    Text(
      text = hudText,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun SelectionInspectorBar(
  document: CreasePatternDocument,
  selection: SelectionState,
  onEditVertexCoords: () -> Unit,
  onFlipMountainValley: () -> Unit,
  onDeleteSelection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedVertex = selection.selectedVertexId?.let { document.vertexById(it) }
  val selectedEdge =
    selection.selectedEdgeId?.let { id -> document.edges.firstOrNull { it.id == id } }

  if (selectedVertex == null && selectedEdge == null) return

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = modifier.widthIn(max = 520.dp).testTag("selection_inspector_bar"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (selectedVertex != null) {
        Text(
          text =
            String.format(
              Locale.US,
              "Vertex (%.1f, %.1f) mm",
              selectedVertex.position.x,
              selectedVertex.position.y,
            ),
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.weight(1f, fill = false),
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .clickable(onClick = onEditVertexCoords)
              .testTag("edit_vertex_coords_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Coords", style = MaterialTheme.typography.labelMedium)
          }
        }
      } else if (selectedEdge != null) {
        val p1 = document.vertexById(selectedEdge.startVertexId)?.position
        val p2 = document.vertexById(selectedEdge.endVertexId)?.position
        val info =
          if (p1 != null && p2 != null) {
            val len = GeometryEngine.distance(p1, p2)
            val ang = GeometryEngine.angleDegrees(p1, p2)
            String.format(
              Locale.US,
              "%s · %.1f mm · %.1f°",
              selectedEdge.assignment.label,
              len,
              ang,
            )
          } else {
            selectedEdge.assignment.label
          }
        Text(
          text = info,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.weight(1f, fill = false),
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .clickable(onClick = onFlipMountainValley)
              .testTag("flip_mv_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("M↔V", style = MaterialTheme.typography.labelMedium)
          }
        }
      }

      IconButton(
        onClick = onDeleteSelection,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_selection_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = "Delete selected element",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun FoldAssignmentBottomBar(
  activeAssignment: FoldAssignment,
  onSelectAssignment: (FoldAssignment) -> Unit,
) {
  val canvasColors = LocalDraftingColors.current
  val dash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FoldAssignment.entries.forEach { assignment ->
          val selected = activeAssignment == assignment
          val lineColor = canvasColors.colorFor(assignment)
          val containerColor =
            if (selected) lineColor.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          val border =
            if (selected) BorderStroke(1.5.dp, lineColor)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            border = border,
            modifier =
              Modifier
                .minimumInteractiveComponentSize()
                .clickable { onSelectAssignment(assignment) }
                .testTag("assignment_${assignment.name.lowercase()}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Canvas(modifier = Modifier.width(18.dp).height(6.dp)) {
                val y = size.height / 2f
                val effect =
                  when (assignment) {
                    FoldAssignment.VALLEY, FoldAssignment.CONSTRUCTION -> dash
                    else -> null
                  }
                drawLine(
                  color = lineColor,
                  start = Offset(0f, y),
                  end = Offset(size.width, y),
                  strokeWidth = 2.6.dp.toPx(),
                  cap = StrokeCap.Round,
                  pathEffect = effect,
                )
              }
              Text(
                text = assignment.label,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (selected) MaterialTheme.colorScheme.onSurface
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
