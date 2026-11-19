package com.example.features.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.core.model.Vec2
import com.example.features.projects.RenameProjectDialog
import com.example.ui.theme.LocalDraftingColors
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  viewModel: EditorViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showRenameDialog by remember { mutableStateOf(false) }
  var showPaperDialog by remember { mutableStateOf(false) }
  var showEditVertexDialog by remember { mutableStateOf(false) }
  var showEditEdgeDialog by remember { mutableStateOf(false) }
  var showGeometrySettingsSheet by remember { mutableStateOf(false) }
  var showAllToolsSheet by remember { mutableStateOf(false) }

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
        showCoordinates = state.showCoordinates,
        onBack = handleExit,
        onRenameClick = { if (doc != null) showRenameDialog = true },
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onSave = { viewModel.saveNow() },
        onOpenGeometrySettings = { showGeometrySettingsSheet = true },
        onZoomToFit = viewModel::zoomToFit,
        onSelectAllInterior = viewModel::selectAllInteriorGeometry,
        onSplitAllIntersections = viewModel::splitAllIntersections,
        onMergeNearbyVertices = viewModel::mergeNearbyVertices,
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isWideTablet = maxWidth >= 680.dp

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
          Row(modifier = Modifier.fillMaxSize()) {
            // Persistent categorized side panel on tablets / wide screens
            if (isWideTablet) {
              AnimatedVisibility(
                visible = state.isToolPanelExpanded,
                enter = fadeIn() + slideInHorizontally { -it / 2 },
                exit = fadeOut() + slideOutHorizontally { -it / 2 },
              ) {
                TabletCategorizedToolPanel(
                  activeTool = state.activeTool,
                  onSelectTool = viewModel::selectTool,
                )
              }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              CreasePatternCanvas(
                document = doc,
                state = state,
                onTransformViewport = viewModel::applyViewportTransform,
                onSnapQuery = viewModel::snapPoint,
                onHoverSnap = viewModel::updateHoverSnap,
                onClearTransientPreviews = viewModel::clearTransientPreviews,
                onFindHitVertexForDrag = viewModel::findHitVertexForDrag,
                onSingleFingerDrag = viewModel::onSingleFingerDrag,
                onSingleFingerDragRelease = viewModel::onSingleFingerDragRelease,
                onCanvasTap = viewModel::onCanvasTap,
                modifier = Modifier.fillMaxSize(),
              )

              // Top HUD row: Context instruction banner + Coordinate HUD
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
              ) {
                ContextInstructionPill(
                  state = state,
                  onToggleMultiSelect = viewModel::toggleMultiSelect,
                  onSelectAll = viewModel::selectAllInteriorGeometry,
                  onSetPolygonSides = viewModel::setRegularPolygonSides,
                  onFinishMultiPoint = viewModel::finishMultiPointPolygon,
                  onCancelStep = viewModel::cancelCurrentToolStep,
                  modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                )

                if (state.showCoordinates) {
                  CoordinateHudPill(state = state, document = doc)
                }
              }

              // Phone Quick Rail OR Tablet Collapse Handle
              if (isWideTablet) {
                Surface(
                  shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                  shadowElevation = 2.dp,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                  modifier =
                    Modifier
                      .align(Alignment.CenterStart)
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
              } else {
                PhoneQuickToolOverlay(
                  activeTool = state.activeTool,
                  isExpanded = state.isToolPanelExpanded,
                  onToggleExpand = viewModel::toggleToolPanel,
                  onSelectTool = viewModel::selectTool,
                  onOpenAllTools = { showAllToolsSheet = true },
                  modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                )
              }

              // Selection & Transformation Inspector Bar
              if (state.selection.hasSelection) {
                SelectionInspectorBar(
                  document = doc,
                  selection = state.selection,
                  precision = state.geometrySettings.coordinatePrecision,
                  onEditVertexCoords = { showEditVertexDialog = true },
                  onEditEdgeProps = { showEditEdgeDialog = true },
                  onFlipMountainValley = viewModel::flipSelectedEdgeMountainValley,
                  onSplitEdge = viewModel::splitSelectedEdgeAtMidpoint,
                  onJoinEdges = viewModel::joinTwoSelectedEdges,
                  onMove = { viewModel.openTransformDialog(DialogKind.MOVE_DELTA) },
                  onRotate = { viewModel.openTransformDialog(DialogKind.ROTATE_ANGLE) },
                  onScale = { viewModel.openTransformDialog(DialogKind.SCALE_FACTOR) },
                  onFlipH = viewModel::flipSelectionHorizontal,
                  onFlipV = viewModel::flipSelectionVertical,
                  onDuplicate = viewModel::duplicateSelectionOffset,
                  onSymmetry = { viewModel.openTransformDialog(DialogKind.SYMMETRY_PICKER) },
                  onDeleteSelection = viewModel::deleteSelection,
                  onClearSelection = viewModel::clearSelection,
                  modifier =
                    Modifier
                      .align(Alignment.BottomCenter)
                      .padding(horizontal = 10.dp, vertical = 10.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  // --- Dialogs & Sheets ---

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
    if (showEditVertexDialog && doc != null && state.selection.singleVertexId != null) {
      doc.vertexById(state.selection.singleVertexId!!)
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

  val selectedEdge =
    if (showEditEdgeDialog && doc != null && state.selection.singleEdgeId != null) {
      doc.edges.firstOrNull { it.id == state.selection.singleEdgeId }
    } else null

  if (selectedEdge != null && doc != null) {
    val pA = doc.vertexById(selectedEdge.startVertexId)?.position
    val pB = doc.vertexById(selectedEdge.endVertexId)?.position
    if (pA != null && pB != null) {
      EditEdgePropertiesDialog(
        currentLengthMm = GeometryEngine.distance(pA, pB),
        currentAngleDeg = GeometryEngine.angleDegrees(pA, pB),
        onDismiss = { showEditEdgeDialog = false },
        onApplyLength = viewModel::setSelectedEdgeLength,
        onApplyAngle = viewModel::setSelectedEdgeAngle,
      )
    }
  }

  if (showGeometrySettingsSheet && doc != null) {
    GeometrySettingsSheet(
      paper = doc.paper,
      showGrid = state.showGrid,
      snapSettings = state.snapSettings,
      geometrySettings = state.geometrySettings,
      onToggleShowGrid = viewModel::toggleShowGrid,
      onUpdateGridSpacing = { spacing ->
        viewModel.updatePaperSettings(doc.paper.widthMm, doc.paper.heightMm, spacing)
      },
      onUpdateSnapSettings = viewModel::updateSnapSettings,
      onUpdateGeometrySettings = viewModel::updateGeometrySettings,
      onSplitAllIntersections = viewModel::splitAllIntersections,
      onMergeNearbyVertices = viewModel::mergeNearbyVertices,
      onDismiss = { showGeometrySettingsSheet = false },
    )
  }

  if (showAllToolsSheet) {
    AllToolsBottomSheet(
      activeTool = state.activeTool,
      onSelectTool = { tool ->
        viewModel.selectTool(tool)
        showAllToolsSheet = false
      },
      onDismiss = { showAllToolsSheet = false },
    )
  }

  val pendingDialog = state.pendingDialog
  if (pendingDialog != null && doc != null) {
    when (pendingDialog.kind) {
      DialogKind.CIRCLE_RADIUS -> {
        val center = pendingDialog.anchorPoint ?: Vec2(doc.paper.widthMm * 0.5, doc.paper.heightMm * 0.5)
        CircleRadiusDialog(
          center = center,
          onDismiss = viewModel::dismissDialog,
          onConfirm = { radius -> viewModel.createCircleWithRadius(center, radius) },
        )
      }
      DialogKind.MOVE_DELTA -> {
        MoveDeltaDialog(
          onDismiss = viewModel::dismissDialog,
          onConfirm = { dx, dy, dup -> viewModel.moveSelectionByDelta(dx, dy, dup) },
        )
      }
      DialogKind.ROTATE_ANGLE -> {
        RotateAngleDialog(
          onDismiss = viewModel::dismissDialog,
          onConfirm = { deg, dup ->
            viewModel.rotateSelection(deg, pendingDialog.anchorPoint, dup)
          },
        )
      }
      DialogKind.SCALE_FACTOR -> {
        ScaleFactorDialog(
          onDismiss = viewModel::dismissDialog,
          onConfirm = { factor, dup ->
            viewModel.scaleSelection(factor, pendingDialog.anchorPoint, dup)
          },
        )
      }
      DialogKind.SYMMETRY_PICKER -> {
        SymmetryDialog(
          paperCenter = Vec2(doc.paper.widthMm * 0.5, doc.paper.heightMm * 0.5),
          onDismiss = viewModel::dismissDialog,
          onApplySymmetry = viewModel::applySymmetrySpec,
          onPickCustomAxis = viewModel::beginCustomSymmetryAxisPick,
        )
      }
    }
  }
}

@Composable
private fun EditorTopToolbar(
  projectName: String,
  canUndo: Boolean,
  canRedo: Boolean,
  isDirty: Boolean,
  isSaving: Boolean,
  showCoordinates: Boolean,
  onBack: () -> Unit,
  onRenameClick: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onSave: () -> Unit,
  onOpenGeometrySettings: () -> Unit,
  onZoomToFit: () -> Unit,
  onSelectAllInterior: () -> Unit,
  onSplitAllIntersections: () -> Unit,
  onMergeNearbyVertices: () -> Unit,
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
              .padding(horizontal = 4.dp, vertical = 4.dp)
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
          onClick = onOpenGeometrySettings,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("geometry_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = "Geometry & Snap Settings",
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
            DropdownMenuItem(
              text = { Text("Select All Creases") },
              leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onSelectAllInterior()
              },
            )
            DropdownMenuItem(
              text = { Text("Split All Intersections") },
              leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onSplitAllIntersections()
              },
            )
            DropdownMenuItem(
              text = { Text("Merge Nearby Vertices") },
              leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onMergeNearbyVertices()
              },
            )
            HorizontalDivider()
            DropdownMenuItem(
              text = { Text(stringResource(R.string.option_show_coords)) },
              trailingIcon = {
                if (showCoordinates) Icon(Icons.Filled.Check, contentDescription = null)
              },
              onClick = onToggleCoordinates,
            )
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
  }
}

@Composable
private fun PhoneQuickToolOverlay(
  activeTool: EditorTool,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onSelectTool: (EditorTool) -> Unit,
  onOpenAllTools: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pinnedTools =
    remember(activeTool) {
      val base =
        mutableListOf(
          EditorTool.SELECT,
          EditorTool.PAN,
          EditorTool.POINT,
          EditorTool.LINE_SEGMENT,
          EditorTool.ERASER,
        )
      if (activeTool !in base) {
        base.add(3, activeTool)
      }
      base
    }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn() + slideInHorizontally { -it / 2 },
      exit = fadeOut() + slideOutHorizontally { -it / 2 },
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
          pinnedTools.forEach { tool ->
            ToolRailItem(
              tool = tool,
              selected = activeTool == tool,
              onClick = { onSelectTool(tool) },
            )
          }
          HorizontalDivider(modifier = Modifier.width(40.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier =
              Modifier
                .width(58.dp)
                .minimumInteractiveComponentSize()
                .clickable(onClick = onOpenAllTools)
                .testTag("open_all_tools_button"),
          ) {
            Column(
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "All Geometry Tools",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "Tools",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }
      }
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
          .clickable(onClick = onToggleExpand)
          .testTag("toggle_tool_panel_button"),
    ) {
      Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
        Icon(
          imageVector =
            if (isExpanded) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
          contentDescription = stringResource(R.string.cd_toggle_tools),
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
private fun TabletCategorizedToolPanel(
  activeTool: EditorTool,
  onSelectTool: (EditorTool) -> Unit,
) {
  val expandedCategories =
    remember {
      mutableStateMapOf(
        ToolCategory.NAVIGATE to true,
        ToolCategory.LINES to true,
        ToolCategory.CIRCLES to true,
        ToolCategory.POLYGONS to false,
        ToolCategory.EDIT to false,
      )
    }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 3.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    modifier = Modifier.width(210.dp).fillMaxHeight(),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      ToolCategory.entries.forEach { category ->
        val isOpen = expandedCategories[category] ?: true
        val tools = EditorTool.entries.filter { it.category == category }
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { expandedCategories[category] = !isOpen }
              .padding(horizontal = 6.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Icon(
            imageVector = if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
        if (isOpen) {
          tools.forEach { tool ->
            val selected = tool == activeTool
            Surface(
              shape = RoundedCornerShape(10.dp),
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .clickable { onSelectTool(tool) }
                  .testTag("tool_${tool.name.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = iconForTool(tool),
                  contentDescription = null,
                  tint =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = tool.label,
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AllToolsBottomSheet(
  activeTool: EditorTool,
  onSelectTool: (EditorTool) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Geometry & Construction Tools", style = MaterialTheme.typography.headlineSmall)
      ToolCategory.entries.forEach { category ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            EditorTool.entries.filter { it.category == category }.forEach { tool ->
              val selected = tool == activeTool
              Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border =
                  if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                  else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier =
                  Modifier
                    .minimumInteractiveComponentSize()
                    .clickable { onSelectTool(tool) }
                    .testTag("sheet_tool_${tool.name.lowercase()}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = iconForTool(tool),
                    contentDescription = null,
                    tint =
                      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                  Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun ToolRailItem(
  tool: EditorTool,
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
        .width(58.dp)
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
        imageVector = iconForTool(tool),
        contentDescription = tool.label,
        tint = fg,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = tool.label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ContextInstructionPill(
  state: EditorUiState,
  onToggleMultiSelect: () -> Unit,
  onSelectAll: () -> Unit,
  onSetPolygonSides: (Int) -> Unit,
  onFinishMultiPoint: () -> Unit,
  onCancelStep: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val canvasColors = LocalDraftingColors.current
  val message = state.statusBannerMessage ?: state.instructionText

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = canvasColors.hudBackground,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      shadowElevation = 2.dp,
    ) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }

    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (state.activeTool == EditorTool.SELECT) {
        FilterChip(
          selected = state.multiSelectEnabled,
          onClick = onToggleMultiSelect,
          label = { Text("Multi") },
        )
        FilterChip(
          selected = false,
          onClick = onSelectAll,
          label = { Text("Select All") },
        )
      }

      if (state.activeTool == EditorTool.REGULAR_POLYGON) {
        listOf(3, 4, 5, 6, 8).forEach { sides ->
          FilterChip(
            selected = state.regularPolygonSides == sides,
            onClick = { onSetPolygonSides(sides) },
            label = { Text("${sides}-gon") },
          )
        }
      }

      val canFinishPoly =
        (state.activeTool == EditorTool.CUSTOM_POLYGON && state.pickedPoints.size >= 3) ||
          (state.activeTool == EditorTool.POLYGON_FROM_VERTICES && state.pickedVertexIds.size >= 3)
      if (canFinishPoly) {
        FilterChip(
          selected = true,
          onClick = onFinishMultiPoint,
          label = { Text("Finish") },
        )
      }

      if (state.pickedPoints.isNotEmpty() ||
        state.pickedVertexIds.isNotEmpty() ||
        state.referenceEdgeId != null ||
        state.referenceCircleId != null
      ) {
        FilterChip(
          selected = false,
          onClick = onCancelStep,
          label = { Text("Cancel") },
        )
      }
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
  val prec = state.geometrySettings.coordinatePrecision.coerceIn(0, 3)
  val fmt = "%.${prec}f"
  val preview = state.previewShape

  val hudText =
    when {
      preview is PreviewShape.SegmentPreview -> {
        val len = GeometryEngine.distance(preview.start, preview.end)
        val ang = GeometryEngine.angleDegrees(preview.start, preview.end)
        String.format(Locale.US, "L: $fmt mm · $fmt° · %d%%", len, ang, zoomPct)
      }
      preview is PreviewShape.CirclePreview -> {
        String.format(Locale.US, "R: $fmt mm · %d%%", preview.radiusMm, zoomPct)
      }
      state.cursorPaperPos != null -> {
        String.format(
          Locale.US,
          "X: $fmt  Y: $fmt · %d%%",
          state.cursorPaperPos.x,
          state.cursorPaperPos.y,
          zoomPct,
        )
      }
      else -> {
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
  precision: Int,
  onEditVertexCoords: () -> Unit,
  onEditEdgeProps: () -> Unit,
  onFlipMountainValley: () -> Unit,
  onSplitEdge: () -> Unit,
  onJoinEdges: () -> Unit,
  onMove: () -> Unit,
  onRotate: () -> Unit,
  onScale: () -> Unit,
  onFlipH: () -> Unit,
  onFlipV: () -> Unit,
  onDuplicate: () -> Unit,
  onSymmetry: () -> Unit,
  onDeleteSelection: () -> Unit,
  onClearSelection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedVertex = selection.singleVertexId?.let { document.vertexById(it) }
  val selectedEdge = selection.singleEdgeId?.let { id -> document.edges.firstOrNull { it.id == id } }
  val fmt = "%.${precision.coerceIn(0, 3)}f"

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    shadowElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = modifier.widthIn(max = 640.dp).testTag("selection_inspector_bar"),
  ) {
    Row(
      modifier =
        Modifier
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when {
        selectedVertex != null -> {
          Text(
            text =
              String.format(
                Locale.US,
                "V($fmt, $fmt)",
                selectedVertex.position.x,
                selectedVertex.position.y,
              ),
            style = MaterialTheme.typography.labelSmall,
          )
          InspectorChip("Coords", Icons.Filled.Edit, onEditVertexCoords, "edit_vertex_coords_button")
        }
        selectedEdge != null -> {
          val p1 = document.vertexById(selectedEdge.startVertexId)?.position
          val p2 = document.vertexById(selectedEdge.endVertexId)?.position
          val info =
            if (p1 != null && p2 != null) {
              val len = GeometryEngine.distance(p1, p2)
              val ang = GeometryEngine.angleDegrees(p1, p2)
              String.format(Locale.US, "%s · $fmt mm · $fmt°", selectedEdge.assignment.label, len, ang)
            } else selectedEdge.assignment.label
          Text(text = info, style = MaterialTheme.typography.labelSmall)
          InspectorChip("Len/∠", Icons.Filled.Straighten, onEditEdgeProps, "edit_edge_props_button")
          InspectorChip("M↔V", Icons.Filled.SwapHoriz, onFlipMountainValley, "flip_mv_button")
          InspectorChip("Split", Icons.AutoMirrored.Filled.CallSplit, onSplitEdge, "split_edge_button")
        }
        else -> {
          val total =
            selection.selectedVertexIds.size +
              selection.selectedEdgeIds.size +
              selection.selectedCircleIds.size +
              selection.selectedArcIds.size
          Text(text = "$total selected", style = MaterialTheme.typography.labelSmall)
          if (selection.selectedEdgeIds.size == 2) {
            InspectorChip("Join", Icons.Filled.CallMerge, onJoinEdges, "join_edges_button")
          }
        }
      }

      InspectorChip("Move", Icons.Filled.OpenWith, onMove, "xform_move_button")
      InspectorChip("Rotate", Icons.AutoMirrored.Filled.RotateRight, onRotate, "xform_rotate_button")
      InspectorChip("Scale", Icons.Filled.OpenInFull, onScale, "xform_scale_button")
      InspectorChip("Dup", Icons.Filled.ContentCopy, onDuplicate, "xform_duplicate_button")
      InspectorChip("Flip H", Icons.Filled.Flip, onFlipH, "xform_flip_h_button")
      InspectorChip("Flip V", Icons.Filled.Flip, onFlipV, "xform_flip_v_button")
      InspectorChip("Sym", Icons.Filled.AutoAwesomeMosaic, onSymmetry, "xform_symmetry_button")

      IconButton(
        onClick = onDeleteSelection,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_selection_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = "Delete selected",
          tint = MaterialTheme.colorScheme.error,
        )
      }
      IconButton(
        onClick = onClearSelection,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear selection")
      }
    }
  }
}

@Composable
private fun InspectorChip(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier =
      Modifier
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
      Text(label, style = MaterialTheme.typography.labelMedium)
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

private fun iconForTool(tool: EditorTool): ImageVector =
  when (tool) {
    EditorTool.SELECT -> Icons.Filled.NearMe
    EditorTool.PAN -> Icons.Filled.OpenWith
    EditorTool.POINT -> Icons.Filled.AddCircleOutline
    EditorTool.ERASER -> Icons.Outlined.AutoFixHigh
    EditorTool.LINE_SEGMENT -> Icons.Filled.Timeline
    EditorTool.RAY -> Icons.Filled.TrendingFlat
    EditorTool.INFINITE_LINE -> Icons.Filled.LinearScale
    EditorTool.PARALLEL_LINE -> Icons.Filled.Straighten
    EditorTool.PERPENDICULAR_LINE -> Icons.Filled.Architecture
    EditorTool.ANGLE_BISECTOR -> Icons.Filled.ChangeHistory
    EditorTool.MIDPOINT -> Icons.Filled.AddCircleOutline
    EditorTool.CIRCLE_CENTER_RADIUS -> Icons.Filled.RadioButtonUnchecked
    EditorTool.CIRCLE_TWO_POINTS -> Icons.Filled.RadioButtonUnchecked
    EditorTool.CIRCLE_THREE_POINTS -> Icons.Filled.RadioButtonUnchecked
    EditorTool.CIRCLE_INTERSECTIONS -> Icons.Filled.RadioButtonUnchecked
    EditorTool.ARC -> Icons.Filled.RadioButtonUnchecked
    EditorTool.TRIANGLE -> Icons.Filled.ChangeHistory
    EditorTool.RECTANGLE -> Icons.Filled.CropSquare
    EditorTool.REGULAR_POLYGON -> Icons.Filled.Hexagon
    EditorTool.CUSTOM_POLYGON -> Icons.Filled.Polyline
    EditorTool.POLYGON_FROM_VERTICES -> Icons.Filled.Pentagon
    EditorTool.MOVE -> Icons.Filled.OpenWith
    EditorTool.ROTATE -> Icons.AutoMirrored.Filled.RotateRight
    EditorTool.SCALE -> Icons.Filled.OpenInFull
    EditorTool.MIRROR_LINE -> Icons.Filled.Flip
    EditorTool.SYMMETRY -> Icons.Filled.AutoAwesomeMosaic
    EditorTool.SPLIT_EDGE -> Icons.AutoMirrored.Filled.CallSplit
    EditorTool.EXTEND_LINE -> Icons.Filled.BorderOuter
    EditorTool.TRIM_LINE -> Icons.Filled.ContentCut
    EditorTool.JOIN_EDGES -> Icons.Filled.CallMerge
  }
