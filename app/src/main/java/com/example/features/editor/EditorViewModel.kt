package com.example.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.geometry.Circle2D
import com.example.core.geometry.ConstructionEngine
import com.example.core.geometry.CreaseEditingEngine
import com.example.core.geometry.GeometryEngine
import com.example.core.geometry.IntersectionEngine
import com.example.core.geometry.Segment2D
import com.example.core.geometry.SymmetrySpec
import com.example.core.geometry.TransformEngine
import com.example.core.history.AddArcCommand
import com.example.core.history.AddCircleCommand
import com.example.core.history.AddCreaseCommand
import com.example.core.history.AddVertexCommand
import com.example.core.history.ChangeEdgeAssignmentCommand
import com.example.core.history.DeleteEdgeCommand
import com.example.core.history.DeleteVertexCommand
import com.example.core.history.DocumentSnapshotCommand
import com.example.core.history.EditorCommand
import com.example.core.history.HistoryManager
import com.example.core.history.MoveVertexCommand
import com.example.core.history.UpdatePaperSpecCommand
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import com.example.core.snapping.SnapEngine
import com.example.core.snapping.SnapKind
import com.example.core.snapping.SnapResult
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repositories.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(
  private val projectId: String,
  private val projectRepository: ProjectRepository,
  private val preferencesRepository: StudioPreferencesRepository,
) : ViewModel() {

  private val historyManager = HistoryManager()
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  private var autoSaveJob: Job? = null
  private var isPickingCustomSymmetryAxis: Boolean = false

  init {
    loadProject()
  }

  private fun loadProject() {
    viewModelScope.launch {
      val prefs = preferencesRepository.preferences.first()
      val doc = projectRepository.loadProject(projectId)
      if (doc == null) {
        _uiState.update {
          it.copy(
            isLoading = false,
            loadError = "Project could not be found.",
          )
        }
        return@launch
      }
      historyManager.clear()
      val intersections =
        IntersectionEngine.computeAllIntersections(doc, prefs.geometryToleranceMm)
      _uiState.update {
        it.copy(
          isLoading = false,
          document = doc,
          showGrid = prefs.defaultShowGrid,
          snapSettings =
            SnapSettingsState(
              snapToGrid = prefs.defaultSnapToGrid,
              snapToVertex = prefs.defaultSnapToVertex,
              snapToEndpoint = prefs.defaultSnapToEndpoint,
              snapToIntersection = prefs.defaultSnapToIntersection,
              snapToMidpoint = prefs.defaultSnapToMidpoint,
              snapToPerpendicular = prefs.defaultSnapToPerpendicular,
              snapToCenter = prefs.defaultSnapToCenter,
              snapToNearestEdge = prefs.defaultSnapToNearestEdge,
            ),
          geometrySettings =
            it.geometrySettings.copy(
              snapDistancePx = prefs.snapDistancePx,
              angleSnapEnabled = prefs.angleSnapEnabled,
              angleSnapStepDeg = prefs.angleSnapStepDeg,
              coordinatePrecision = prefs.coordinatePrecision,
              showConstructionLines = prefs.showConstructionLines,
              showIntersections = prefs.showIntersections,
              geometryToleranceMm = prefs.geometryToleranceMm,
            ),
          showCoordinates = prefs.showCoordinateHud,
          lastSavedAt = doc.updatedAt,
          canUndo = false,
          canRedo = false,
          isDirty = false,
          cachedIntersections = intersections,
        )
      }
    }
  }

  fun selectTool(tool: EditorTool) {
    isPickingCustomSymmetryAxis = false
    if (tool == EditorTool.SYMMETRY) {
      _uiState.update {
        it.copy(
          activeTool = tool,
          pendingDialog = PendingDialogRequest(DialogKind.SYMMETRY_PICKER),
          pickedPoints = emptyList(),
          pickedVertexIds = emptyList(),
          referenceEdgeId = null,
          referenceCircleId = null,
          previewShape = null,
          dragMovePreview = null,
        )
      }
      return
    }
    _uiState.update {
      it.copy(
        activeTool = tool,
        pickedPoints = emptyList(),
        pickedVertexIds = emptyList(),
        referenceEdgeId = null,
        referenceCircleId = null,
        previewShape = null,
        dragMovePreview = null,
        snapIndicator = null,
        statusBannerMessage = null,
      )
    }
  }

  fun toggleMultiSelect() {
    _uiState.update { it.copy(multiSelectEnabled = !it.multiSelectEnabled) }
  }

  fun setRegularPolygonSides(sides: Int) {
    _uiState.update { it.copy(regularPolygonSides = sides.coerceIn(3, 16)) }
  }

  fun selectAssignment(assignment: FoldAssignment) {
    val state = _uiState.value
    val doc = state.document
    if (state.selection.selectedEdgeIds.isNotEmpty() && doc != null) {
      if (state.selection.selectedEdgeIds.size == 1) {
        val edge = doc.edges.firstOrNull { it.id == state.selection.selectedEdgeIds.first() }
        if (edge != null && edge.assignment != assignment) {
          executeCommand(
            ChangeEdgeAssignmentCommand(
              edgeId = edge.id,
              oldAssignment = edge.assignment,
              newAssignment = assignment,
            )
          )
        }
      } else {
        val updatedEdges =
          doc.edges.map { e ->
            if (e.id in state.selection.selectedEdgeIds) e.copy(assignment = assignment) else e
          }
        val updated = doc.copy(edges = updatedEdges, updatedAt = System.currentTimeMillis())
        commitDocumentSnapshot("Assign ${assignment.label}", doc, updated)
      }
    }
    _uiState.update { it.copy(activeAssignment = assignment) }
  }

  fun toggleToolPanel() {
    _uiState.update { it.copy(isToolPanelExpanded = !it.isToolPanelExpanded) }
  }

  fun toggleShowGrid() {
    _uiState.update { it.copy(showGrid = !it.showGrid) }
  }

  fun toggleShowCoordinates() {
    _uiState.update { it.copy(showCoordinates = !it.showCoordinates) }
  }

  fun updateSnapSettings(transform: (SnapSettingsState) -> SnapSettingsState) {
    _uiState.update { state ->
      val updated = transform(state.snapSettings)
      viewModelScope.launch {
        preferencesRepository.setDefaultSnapToGrid(updated.snapToGrid)
        preferencesRepository.setDefaultSnapToVertex(updated.snapToVertex)
        preferencesRepository.setDefaultSnapToEndpoint(updated.snapToEndpoint)
        preferencesRepository.setDefaultSnapToIntersection(updated.snapToIntersection)
        preferencesRepository.setDefaultSnapToMidpoint(updated.snapToMidpoint)
        preferencesRepository.setDefaultSnapToPerpendicular(updated.snapToPerpendicular)
        preferencesRepository.setDefaultSnapToCenter(updated.snapToCenter)
        preferencesRepository.setDefaultSnapToNearestEdge(updated.snapToNearestEdge)
      }
      state.copy(snapSettings = updated)
    }
  }

  fun updateGeometrySettings(transform: (com.example.core.model.GeometrySettings) -> com.example.core.model.GeometrySettings) {
    _uiState.update { state ->
      val updated = transform(state.geometrySettings)
      val recomputed =
        if (updated.geometryToleranceMm != state.geometrySettings.geometryToleranceMm && state.document != null) {
          IntersectionEngine.computeAllIntersections(state.document, updated.geometryToleranceMm)
        } else {
          state.cachedIntersections
        }
      viewModelScope.launch {
        preferencesRepository.setSnapDistancePx(updated.snapDistancePx)
        preferencesRepository.setAngleSnapEnabled(updated.angleSnapEnabled)
        preferencesRepository.setAngleSnapStepDeg(updated.angleSnapStepDeg)
        preferencesRepository.setCoordinatePrecision(updated.coordinatePrecision)
        preferencesRepository.setShowConstructionLines(updated.showConstructionLines)
        preferencesRepository.setShowIntersections(updated.showIntersections)
        preferencesRepository.setGeometryToleranceMm(updated.geometryToleranceMm)
      }
      state.copy(geometrySettings = updated, cachedIntersections = recomputed)
    }
  }

  fun zoomToFit() {
    _uiState.update { it.copy(viewport = EditorViewportState()) }
  }

  fun applyViewportTransform(zoomDelta: Float, panDeltaX: Float, panDeltaY: Float) {
    _uiState.update { state ->
      val newZoom = (state.viewport.zoom * zoomDelta).coerceIn(0.2f, 24f)
      state.copy(
        viewport =
          state.viewport.copy(
            zoom = newZoom,
            panOffsetX = state.viewport.panOffsetX + panDeltaX,
            panOffsetY = state.viewport.panOffsetY + panDeltaY,
          )
      )
    }
  }

  fun snapPoint(rawPaperPoint: Vec2, pixelsPerMm: Float, anchorPoint: Vec2? = null): SnapResult {
    val state = _uiState.value
    val doc = state.document ?: return SnapResult(rawPaperPoint, SnapKind.NONE)
    val safeScale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
    val snapRadiusMm = (state.geometrySettings.snapDistancePx / safeScale).coerceIn(1.5, 22.0)
    val effectiveAnchor = anchorPoint ?: state.pickedPoints.lastOrNull()
    return SnapEngine.resolveSnap(
      rawPaperPoint = rawPaperPoint,
      document = doc,
      config = state.snapSettings.toSnapConfig(doc.paper.gridSpacingMm, snapRadiusMm, state.geometrySettings),
      anchorPoint = effectiveAnchor,
      precomputedIntersections = state.cachedIntersections,
    )
  }

  fun updateHoverSnap(rawPaperPoint: Vec2?, snapResult: SnapResult?) {
    _uiState.update {
      it.copy(
        cursorPaperPos = snapResult?.position ?: rawPaperPoint,
        snapIndicator =
          if (snapResult != null && snapResult.kind != SnapKind.NONE) {
            SnapIndicator(snapResult.position, snapResult.kind)
          } else {
            null
          },
      )
    }
  }

  fun clearTransientPreviews() {
    _uiState.update {
      it.copy(
        snapIndicator = null,
        previewShape = null,
        dragMovePreview = null,
      )
    }
  }

  fun cancelCurrentToolStep() {
    isPickingCustomSymmetryAxis = false
    _uiState.update {
      it.copy(
        pickedPoints = emptyList(),
        pickedVertexIds = emptyList(),
        referenceEdgeId = null,
        referenceCircleId = null,
        previewShape = null,
        dragMovePreview = null,
        snapIndicator = null,
        statusBannerMessage = null,
      )
    }
  }

  fun finishMultiPointPolygon() {
    val state = _uiState.value
    val doc = state.document ?: return
    val tol = state.geometrySettings.geometryToleranceMm
    when (state.activeTool) {
      EditorTool.CUSTOM_POLYGON -> {
        if (state.pickedPoints.size >= 3) {
          val updated = TransformEngine.addPolyline(doc, state.pickedPoints, state.activeAssignment, closed = true, toleranceMm = tol)
          commitDocumentSnapshot("Add polygon", doc, updated)
        }
      }
      EditorTool.POLYGON_FROM_VERTICES -> {
        if (state.pickedVertexIds.size >= 3) {
          val pts = state.pickedVertexIds.mapNotNull { doc.vertexById(it)?.position }
          if (pts.size >= 3) {
            val updated = TransformEngine.addPolyline(doc, pts, state.activeAssignment, closed = true, toleranceMm = tol)
            commitDocumentSnapshot("Polygon from vertices", doc, updated)
          }
        }
      }
      else -> Unit
    }
    cancelCurrentToolStep()
  }

  // --- Dragging & Tapping Dispatch ---

  fun onSingleFingerDrag(
    startRawPaper: Vec2,
    startSnap: SnapResult,
    curRawPaper: Vec2,
    curSnap: SnapResult,
    grabbedVertex: Vertex?,
    moveSelectionActive: Boolean,
  ) {
    val state = _uiState.value
    when (state.activeTool) {
      EditorTool.SELECT -> {
        if (grabbedVertex != null && !grabbedVertex.isLocked) {
          val delta = curSnap.position - grabbedVertex.position
          val offsets =
            if (grabbedVertex.id in state.selection.selectedVertexIds && state.selection.selectedVertexIds.size > 1) {
              buildMoveOffsetsForSelection(state.document, state.selection, delta)
            } else {
              mapOf(grabbedVertex.id to curSnap.position)
            }
          _uiState.update {
            it.copy(
              selection =
                if (grabbedVertex.id in it.selection.selectedVertexIds) it.selection
                else SelectionState(selectedVertexIds = setOf(grabbedVertex.id)),
              dragMovePreview = DragMoveVertexPreview(offsets),
              cursorPaperPos = curSnap.position,
              snapIndicator =
                if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
            )
          }
        } else {
          // Box selection preview
          _uiState.update {
            it.copy(
              previewShape = PreviewShape.BoxSelectPreview(startRawPaper, curRawPaper),
              cursorPaperPos = curRawPaper,
            )
          }
        }
      }
      EditorTool.MOVE -> {
        if (moveSelectionActive || grabbedVertex != null) {
          val doc = state.document ?: return
          val delta = curSnap.position - startSnap.position
          val offsets =
            if (state.selection.hasSelection) {
              buildMoveOffsetsForSelection(doc, state.selection, delta)
            } else if (grabbedVertex != null && !grabbedVertex.isLocked) {
              mapOf(grabbedVertex.id to curSnap.position)
            } else {
              emptyMap()
            }
          if (offsets.isNotEmpty()) {
            _uiState.update {
              it.copy(
                dragMovePreview = DragMoveVertexPreview(offsets),
                cursorPaperPos = curSnap.position,
                snapIndicator =
                  if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
              )
            }
          }
        }
      }
      EditorTool.LINE_SEGMENT, EditorTool.RAY, EditorTool.INFINITE_LINE -> {
        _uiState.update {
          it.copy(
            previewShape = PreviewShape.SegmentPreview(startSnap.position, curSnap.position),
            cursorPaperPos = curSnap.position,
            snapIndicator =
              if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
          )
        }
      }
      EditorTool.CIRCLE_CENTER_RADIUS -> {
        val radius = GeometryEngine.distance(startSnap.position, curSnap.position)
        _uiState.update {
          it.copy(
            previewShape = PreviewShape.CirclePreview(startSnap.position, radius),
            cursorPaperPos = curSnap.position,
            snapIndicator =
              if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
          )
        }
      }
      EditorTool.CIRCLE_TWO_POINTS -> {
        val center = GeometryEngine.midpoint(startSnap.position, curSnap.position)
        val radius = GeometryEngine.distance(startSnap.position, curSnap.position) * 0.5
        _uiState.update {
          it.copy(
            previewShape = PreviewShape.CirclePreview(center, radius),
            cursorPaperPos = curSnap.position,
            snapIndicator =
              if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
          )
        }
      }
      EditorTool.RECTANGLE -> {
        val corners = ConstructionEngine.rectangleVertices(startSnap.position, curSnap.position)
        _uiState.update {
          it.copy(
            previewShape =
              if (corners.size == 4) PreviewShape.PolygonPreview(corners, closed = true) else null,
            cursorPaperPos = curSnap.position,
            snapIndicator =
              if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
          )
        }
      }
      EditorTool.REGULAR_POLYGON -> {
        val pts =
          ConstructionEngine.regularPolygonVertices(
            center = startSnap.position,
            circumradiusPoint = curSnap.position,
            sides = state.regularPolygonSides,
          )
        _uiState.update {
          it.copy(
            previewShape =
              if (pts.size >= 3) PreviewShape.PolygonPreview(pts, closed = true) else null,
            cursorPaperPos = curSnap.position,
            snapIndicator =
              if (curSnap.kind != SnapKind.NONE) SnapIndicator(curSnap.position, curSnap.kind) else null,
          )
        }
      }
      else -> {
        updateHoverSnap(curRawPaper, curSnap)
      }
    }
  }

  fun onSingleFingerDragRelease(
    startRawPaper: Vec2,
    startSnap: SnapResult,
    endRawPaper: Vec2,
    endSnap: SnapResult,
    grabbedVertex: Vertex?,
  ) {
    val state = _uiState.value
    val doc = state.document ?: return
    val tol = state.geometrySettings.geometryToleranceMm

    when (state.activeTool) {
      EditorTool.SELECT -> {
        val dragPreview = state.dragMovePreview
        if (dragPreview != null && dragPreview.vertexOffsets.isNotEmpty()) {
          commitDraggedVertices(doc, dragPreview.vertexOffsets)
        } else if (state.previewShape is PreviewShape.BoxSelectPreview) {
          selectWithinBox(startRawPaper, endRawPaper)
        }
      }
      EditorTool.MOVE -> {
        val dragPreview = state.dragMovePreview
        if (dragPreview != null && dragPreview.vertexOffsets.isNotEmpty()) {
          commitDraggedVertices(doc, dragPreview.vertexOffsets)
        }
      }
      EditorTool.LINE_SEGMENT -> {
        commitLineSegment(startSnap.position, startSnap.snappedVertexId, endSnap.position, endSnap.snappedVertexId)
      }
      EditorTool.RAY -> {
        commitRay(startSnap.position, endSnap.position)
      }
      EditorTool.INFINITE_LINE -> {
        commitInfiniteLine(startSnap.position, endSnap.position)
      }
      EditorTool.CIRCLE_CENTER_RADIUS -> {
        val r = GeometryEngine.distance(startSnap.position, endSnap.position)
        val circle = ConstructionEngine.circleFromCenterAndRadius(startSnap.position, r, state.activeAssignment)
        if (circle != null) executeCommand(AddCircleCommand(circle))
      }
      EditorTool.CIRCLE_TWO_POINTS -> {
        val circle = ConstructionEngine.circleFromTwoPoints(startSnap.position, endSnap.position, state.activeAssignment)
        if (circle != null) executeCommand(AddCircleCommand(circle))
      }
      EditorTool.RECTANGLE -> {
        val pts = ConstructionEngine.rectangleVertices(startSnap.position, endSnap.position)
        if (pts.size == 4) {
          val updated = TransformEngine.addPolyline(doc, pts, state.activeAssignment, closed = true, toleranceMm = tol)
          commitDocumentSnapshot("Add rectangle", doc, updated)
        }
      }
      EditorTool.REGULAR_POLYGON -> {
        val pts = ConstructionEngine.regularPolygonVertices(startSnap.position, endSnap.position, state.regularPolygonSides)
        if (pts.size >= 3) {
          val updated = TransformEngine.addPolyline(doc, pts, state.activeAssignment, closed = true, toleranceMm = tol)
          commitDocumentSnapshot("Add ${state.regularPolygonSides}-gon", doc, updated)
        }
      }
      else -> Unit
    }

    _uiState.update {
      it.copy(
        previewShape = null,
        dragMovePreview = null,
        snapIndicator = null,
      )
    }
  }

  fun onCanvasTap(rawPaper: Vec2, snap: SnapResult, pixelsPerMm: Float) {
    val state = _uiState.value
    val doc = state.document ?: return
    val tol = state.geometrySettings.geometryToleranceMm

    if (isPickingCustomSymmetryAxis) {
      val pts = state.pickedPoints + snap.position
      if (pts.size < 2) {
        _uiState.update {
          it.copy(
            pickedPoints = pts,
            previewShape = PreviewShape.SegmentPreview(pts[0], pts[0]),
            statusBannerMessage = "Custom Symmetry: Tap second point on mirror axis",
          )
        }
      } else {
        isPickingCustomSymmetryAxis = false
        applySymmetrySpec(SymmetrySpec.CustomAxis(pts[0], pts[1]))
        _uiState.update {
          it.copy(pickedPoints = emptyList(), previewShape = null, statusBannerMessage = null)
        }
      }
      return
    }

    when (state.activeTool) {
      EditorTool.PAN -> Unit
      EditorTool.SELECT -> selectAt(rawPaper, pixelsPerMm)
      EditorTool.POINT -> placePoint(snap)
      EditorTool.ERASER -> eraseAt(rawPaper, pixelsPerMm)

      EditorTool.LINE_SEGMENT -> {
        val prev = state.pickedPoints
        if (prev.isEmpty()) {
          _uiState.update {
            it.copy(
              pickedPoints = listOf(snap.position),
              pickedVertexIds = listOfNotNull(snap.snappedVertexId),
              previewShape = PreviewShape.SegmentPreview(snap.position, snap.position),
            )
          }
        } else {
          commitLineSegment(prev.first(), state.pickedVertexIds.firstOrNull(), snap.position, snap.snappedVertexId)
          _uiState.update { it.copy(pickedPoints = emptyList(), pickedVertexIds = emptyList(), previewShape = null) }
        }
      }

      EditorTool.RAY -> {
        val prev = state.pickedPoints
        if (prev.isEmpty()) {
          _uiState.update { it.copy(pickedPoints = listOf(snap.position)) }
        } else {
          commitRay(prev.first(), snap.position)
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.INFINITE_LINE -> {
        val prev = state.pickedPoints
        if (prev.isEmpty()) {
          _uiState.update { it.copy(pickedPoints = listOf(snap.position)) }
        } else {
          commitInfiniteLine(prev.first(), snap.position)
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.PARALLEL_LINE, EditorTool.PERPENDICULAR_LINE -> {
        val refId = state.referenceEdgeId
        if (refId == null) {
          val hitEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
          if (hitEdge != null) {
            _uiState.update { it.copy(referenceEdgeId = hitEdge.id) }
          }
        } else {
          val refEdge = doc.edges.firstOrNull { it.id == refId }
          val pA = refEdge?.let { doc.vertexById(it.startVertexId)?.position }
          val pB = refEdge?.let { doc.vertexById(it.endVertexId)?.position }
          if (pA != null && pB != null) {
            val line =
              if (state.activeTool == EditorTool.PARALLEL_LINE) {
                ConstructionEngine.parallelLineThrough(snap.position, pA, pB)
              } else {
                ConstructionEngine.perpendicularLineThrough(snap.position, pA, pB)
              }
            val clipped = line?.let { IntersectionEngine.clipLineToPaper(it, doc.paper) }
            if (clipped != null) {
              val updated =
                TransformEngine.addSegmentWithDeduplication(
                  doc,
                  clipped.a,
                  clipped.b,
                  state.activeAssignment,
                  tol,
                )
              commitDocumentSnapshot(state.activeTool.label, doc, updated)
            }
          }
          _uiState.update { it.copy(referenceEdgeId = null, previewShape = null) }
        }
      }

      EditorTool.ANGLE_BISECTOR -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 3) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val ray = ConstructionEngine.angleBisector(pts[0], pts[1], pts[2])
          val clipped = ray?.let { IntersectionEngine.clipRayToPaper(it, doc.paper) }
          if (clipped != null) {
            val updated =
              TransformEngine.addSegmentWithDeduplication(
                doc,
                clipped.a,
                clipped.b,
                state.activeAssignment,
                tol,
              )
            commitDocumentSnapshot("Add angle bisector", doc, updated)
          }
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.MIDPOINT -> {
        val hitEdge = if (state.pickedPoints.isEmpty()) findHitEdge(doc, rawPaper, pixelsPerMm) else null
        if (hitEdge != null && snap.kind != SnapKind.VERTEX && snap.kind != SnapKind.ENDPOINT) {
          val pA = doc.vertexById(hitEdge.startVertexId)?.position
          val pB = doc.vertexById(hitEdge.endVertexId)?.position
          if (pA != null && pB != null) {
            val mid = GeometryEngine.midpoint(pA, pB)
            addPointAtPosition(mid)
          }
        } else {
          val pts = state.pickedPoints + snap.position
          if (pts.size < 2) {
            _uiState.update { it.copy(pickedPoints = pts) }
          } else {
            val mid = GeometryEngine.midpoint(pts[0], pts[1])
            addPointAtPosition(mid)
            _uiState.update { it.copy(pickedPoints = emptyList()) }
          }
        }
      }

      EditorTool.CIRCLE_CENTER_RADIUS -> {
        _uiState.update {
          it.copy(pendingDialog = PendingDialogRequest(DialogKind.CIRCLE_RADIUS, anchorPoint = snap.position))
        }
      }

      EditorTool.CIRCLE_TWO_POINTS -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 2) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val circle = ConstructionEngine.circleFromTwoPoints(pts[0], pts[1], state.activeAssignment)
          if (circle != null) executeCommand(AddCircleCommand(circle))
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.CIRCLE_THREE_POINTS -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 3) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val circle = ConstructionEngine.circleThroughThreePoints(pts[0], pts[1], pts[2], state.activeAssignment)
          if (circle != null) {
            executeCommand(AddCircleCommand(circle))
          } else {
            _uiState.update { it.copy(statusBannerMessage = "3 points are collinear — circle skipped") }
          }
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.CIRCLE_INTERSECTIONS -> {
        val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
        val hitDist = (22.0 / scale).coerceIn(2.0, 18.0)
        val firstCircleId = state.referenceCircleId
        if (firstCircleId == null) {
          val c = GeometryEngine.findNearestCircle(doc, rawPaper, hitDist)
          if (c != null) _uiState.update { it.copy(referenceCircleId = c.id) }
        } else {
          val c1 = doc.circles.firstOrNull { it.id == firstCircleId }
          val c2 = GeometryEngine.findNearestCircle(doc, rawPaper, hitDist)?.takeIf { it.id != firstCircleId }
          val hitEdge = if (c2 == null) findHitEdge(doc, rawPaper, pixelsPerMm) else null
          if (c1 != null && c2 != null) {
            val res =
              IntersectionEngine.intersectCircleCircle(
                Circle2D(c1.center, c1.radiusMm),
                Circle2D(c2.center, c2.radiusMm),
                tol,
              )
            val pts =
              when (res) {
                is com.example.core.geometry.CircleIntersectionResult.Tangent -> listOf(res.point)
                is com.example.core.geometry.CircleIntersectionResult.TwoPoints -> listOf(res.first, res.second)
                else -> emptyList()
              }
            materializeVerticesAt(pts, "Circle-circle intersections")
          } else if (c1 != null && hitEdge != null) {
            val a = doc.vertexById(hitEdge.startVertexId)?.position
            val b = doc.vertexById(hitEdge.endVertexId)?.position
            if (a != null && b != null) {
              val pts = IntersectionEngine.intersectSegmentCircle(Segment2D(a, b), Circle2D(c1.center, c1.radiusMm), tol)
              materializeVerticesAt(pts, "Circle-line intersections")
            }
          }
          _uiState.update { it.copy(referenceCircleId = null) }
        }
      }

      EditorTool.ARC -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 3) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val arc = ConstructionEngine.arcFromCenterStartEnd(pts[0], pts[1], pts[2], state.activeAssignment)
          if (arc != null) executeCommand(AddArcCommand(arc))
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.TRIANGLE -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 3) {
          _uiState.update {
            it.copy(
              pickedPoints = pts,
              previewShape = PreviewShape.PolygonPreview(pts, closed = false),
            )
          }
        } else {
          val updated = TransformEngine.addPolyline(doc, pts, state.activeAssignment, closed = true, toleranceMm = tol)
          commitDocumentSnapshot("Add triangle", doc, updated)
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.RECTANGLE -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 2) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val rect = ConstructionEngine.rectangleVertices(pts[0], pts[1])
          if (rect.size == 4) {
            val updated = TransformEngine.addPolyline(doc, rect, state.activeAssignment, closed = true, toleranceMm = tol)
            commitDocumentSnapshot("Add rectangle", doc, updated)
          }
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.REGULAR_POLYGON -> {
        val pts = state.pickedPoints + snap.position
        if (pts.size < 2) {
          _uiState.update { it.copy(pickedPoints = pts) }
        } else {
          val poly = ConstructionEngine.regularPolygonVertices(pts[0], pts[1], state.regularPolygonSides)
          if (poly.size >= 3) {
            val updated = TransformEngine.addPolyline(doc, poly, state.activeAssignment, closed = true, toleranceMm = tol)
            commitDocumentSnapshot("Add ${state.regularPolygonSides}-gon", doc, updated)
          }
          _uiState.update { it.copy(pickedPoints = emptyList(), previewShape = null) }
        }
      }

      EditorTool.CUSTOM_POLYGON -> {
        val pts = state.pickedPoints + snap.position
        _uiState.update {
          it.copy(
            pickedPoints = pts,
            previewShape = PreviewShape.PolygonPreview(pts, closed = false),
          )
        }
      }

      EditorTool.POLYGON_FROM_VERTICES -> {
        val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
        val hitDist = (24.0 / scale).coerceIn(2.5, 20.0)
        val v = GeometryEngine.findNearestVertex(doc, rawPaper, hitDist)
        if (v != null) {
          val nextIds = if (v.id in state.pickedVertexIds) state.pickedVertexIds else state.pickedVertexIds + v.id
          val pts = nextIds.mapNotNull { doc.vertexById(it)?.position }
          _uiState.update {
            it.copy(
              pickedVertexIds = nextIds,
              previewShape = PreviewShape.PolygonPreview(pts, closed = false),
            )
          }
        }
      }

      EditorTool.MOVE -> {
        _uiState.update {
          it.copy(pendingDialog = PendingDialogRequest(DialogKind.MOVE_DELTA))
        }
      }

      EditorTool.ROTATE -> {
        _uiState.update {
          it.copy(pendingDialog = PendingDialogRequest(DialogKind.ROTATE_ANGLE, anchorPoint = snap.position))
        }
      }

      EditorTool.SCALE -> {
        _uiState.update {
          it.copy(pendingDialog = PendingDialogRequest(DialogKind.SCALE_FACTOR, anchorPoint = snap.position))
        }
      }

      EditorTool.MIRROR_LINE -> {
        val axisEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
        if (axisEdge != null) {
          val pA = doc.vertexById(axisEdge.startVertexId)?.position
          val pB = doc.vertexById(axisEdge.endVertexId)?.position
          if (pA != null && pB != null) {
            applySymmetrySpec(SymmetrySpec.CustomAxis(pA, pB))
          }
        }
      }

      EditorTool.SYMMETRY -> {
        _uiState.update { it.copy(pendingDialog = PendingDialogRequest(DialogKind.SYMMETRY_PICKER)) }
      }

      EditorTool.SPLIT_EDGE -> {
        val hitEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
        if (hitEdge != null) {
          val splitPt =
            if (snap.kind != SnapKind.NONE && snap.kind != SnapKind.NEAREST_EDGE) snap.position
            else rawPaper
          val updated = CreaseEditingEngine.splitEdgeAtPoint(doc, hitEdge.id, splitPt, tol)
          if (updated != doc) commitDocumentSnapshot("Split edge", doc, updated)
        }
      }

      EditorTool.EXTEND_LINE -> {
        val hitEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
        if (hitEdge != null) {
          val updated = CreaseEditingEngine.extendEdgeTowardTap(doc, hitEdge.id, rawPaper, tol)
          if (updated != doc) commitDocumentSnapshot("Extend edge", doc, updated)
        }
      }

      EditorTool.TRIM_LINE -> {
        val hitEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
        if (hitEdge != null) {
          val updated = CreaseEditingEngine.trimEdgeAtTap(doc, hitEdge.id, rawPaper, tol)
          if (updated != doc) commitDocumentSnapshot("Trim edge", doc, updated)
        }
      }

      EditorTool.JOIN_EDGES -> {
        val hitEdge = findHitEdge(doc, rawPaper, pixelsPerMm)
        if (hitEdge != null) {
          val firstId = state.referenceEdgeId
          if (firstId == null) {
            _uiState.update { it.copy(referenceEdgeId = hitEdge.id) }
          } else {
            val updated = CreaseEditingEngine.joinCompatibleEdges(doc, firstId, hitEdge.id, tol.coerceAtLeast(0.05))
            if (updated != doc) {
              commitDocumentSnapshot("Join edges", doc, updated)
            } else {
              _uiState.update { it.copy(statusBannerMessage = "Edges must share a degree-2 vertex, be collinear, and match fold type") }
            }
            _uiState.update { it.copy(referenceEdgeId = null) }
          }
        }
      }
    }
  }

  // --- Construction & Editing Actions ---

  private fun addPointAtPosition(pos: Vec2) {
    val doc = _uiState.value.document ?: return
    val tol = _uiState.value.geometrySettings.geometryToleranceMm
    val existing = GeometryEngine.findDuplicateVertex(doc.vertices, pos, tol)
    if (existing != null) {
      _uiState.update { it.copy(selection = SelectionState(selectedVertexIds = setOf(existing.id))) }
      return
    }
    val v = Vertex(id = GeometryEngine.newId(), position = pos)
    executeCommand(AddVertexCommand(v))
    _uiState.update { it.copy(selection = SelectionState(selectedVertexIds = setOf(v.id))) }
  }

  private fun materializeVerticesAt(points: List<Vec2>, description: String) {
    val doc = _uiState.value.document ?: return
    if (points.isEmpty()) return
    val tol = _uiState.value.geometrySettings.geometryToleranceMm
    val vertices = doc.vertices.toMutableList()
    val createdIds = mutableSetOf<String>()
    for (pt in points) {
      val existing = GeometryEngine.findDuplicateVertex(vertices, pt, tol)
      if (existing != null) {
        createdIds.add(existing.id)
      } else {
        val v = Vertex(GeometryEngine.newId(), pt)
        vertices.add(v)
        createdIds.add(v.id)
      }
    }
    val updated = doc.copy(vertices = vertices, updatedAt = System.currentTimeMillis())
    commitDocumentSnapshot(description, doc, updated)
    _uiState.update { it.copy(selection = SelectionState(selectedVertexIds = createdIds)) }
  }

  fun placePoint(snapResult: SnapResult) {
    val doc = _uiState.value.document ?: return
    val tol = _uiState.value.geometrySettings.geometryToleranceMm
    val existing =
      snapResult.snappedVertexId?.let { doc.vertexById(it) }
        ?: GeometryEngine.findDuplicateVertex(doc.vertices, snapResult.position, tol)
    if (existing != null) {
      _uiState.update {
        it.copy(
          selection = SelectionState(selectedVertexIds = setOf(existing.id)),
          cursorPaperPos = existing.position,
        )
      }
      return
    }
    val vertex = Vertex(id = GeometryEngine.newId(), position = snapResult.position)
    executeCommand(AddVertexCommand(vertex))
    _uiState.update {
      it.copy(
        selection = SelectionState(selectedVertexIds = setOf(vertex.id)),
        cursorPaperPos = vertex.position,
      )
    }
  }

  fun commitLineSegment(
    startPos: Vec2,
    startVertexId: String?,
    endPos: Vec2,
    endVertexId: String?,
  ) {
    val state = _uiState.value
    val doc = state.document ?: return
    val tol = state.geometrySettings.geometryToleranceMm
    if (GeometryEngine.distance(startPos, endPos) < max(0.3, tol)) return

    val existingStart =
      startVertexId?.let { doc.vertexById(it) }
        ?: GeometryEngine.findDuplicateVertex(doc.vertices, startPos, tol)
    val startVertex = existingStart ?: Vertex(id = GeometryEngine.newId(), position = startPos)

    val existingEnd =
      endVertexId?.let { doc.vertexById(it) }
        ?: GeometryEngine.findDuplicateVertex(doc.vertices, endPos, tol)
    val endVertex =
      if (existingEnd != null && existingEnd.id != startVertex.id) existingEnd
      else Vertex(id = GeometryEngine.newId(), position = endPos)

    if (startVertex.id == endVertex.id) return

    val duplicateEdge = GeometryEngine.findDuplicateEdge(doc.edges, startVertex.id, endVertex.id)
    if (duplicateEdge != null) {
      if (duplicateEdge.assignment != state.activeAssignment) {
        executeCommand(
          ChangeEdgeAssignmentCommand(
            edgeId = duplicateEdge.id,
            oldAssignment = duplicateEdge.assignment,
            newAssignment = state.activeAssignment,
          )
        )
      }
      _uiState.update { it.copy(selection = SelectionState(selectedEdgeIds = setOf(duplicateEdge.id))) }
      return
    }

    val newEdge =
      Edge(
        id = GeometryEngine.newId(),
        startVertexId = startVertex.id,
        endVertexId = endVertex.id,
        assignment = state.activeAssignment,
      )
    executeCommand(
      AddCreaseCommand(
        createdStartVertex = if (existingStart == null) startVertex else null,
        createdEndVertex = if (existingEnd == null) endVertex else null,
        edge = newEdge,
      )
    )
    _uiState.update { it.copy(selection = SelectionState(selectedEdgeIds = setOf(newEdge.id))) }
  }

  private fun commitRay(origin: Vec2, directionPoint: Vec2) {
    val state = _uiState.value
    val doc = state.document ?: return
    val dir = GeometryEngine.normalize(directionPoint - origin) ?: return
    val ray = com.example.core.geometry.Ray2D(origin, dir)
    val clipped = IntersectionEngine.clipRayToPaper(ray, doc.paper) ?: return
    val updated =
      TransformEngine.addSegmentWithDeduplication(
        doc,
        clipped.a,
        clipped.b,
        state.activeAssignment,
        state.geometrySettings.geometryToleranceMm,
      )
    commitDocumentSnapshot("Add ray", doc, updated)
  }

  private fun commitInfiniteLine(p1: Vec2, p2: Vec2) {
    val state = _uiState.value
    val doc = state.document ?: return
    val dir = GeometryEngine.normalize(p2 - p1) ?: return
    val line = com.example.core.geometry.Line2D(p1, dir)
    val clipped = IntersectionEngine.clipLineToPaper(line, doc.paper) ?: return
    val updated =
      TransformEngine.addSegmentWithDeduplication(
        doc,
        clipped.a,
        clipped.b,
        state.activeAssignment,
        state.geometrySettings.geometryToleranceMm,
      )
    commitDocumentSnapshot("Add infinite line", doc, updated)
  }

  private fun findHitEdge(doc: CreasePatternDocument, paperPoint: Vec2, pixelsPerMm: Float): Edge? {
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
    val edgeHitMm = (20.0 / scale).coerceIn(2.0, 16.0)
    return GeometryEngine.findNearestEdge(doc, paperPoint, edgeHitMm)
  }

  fun findHitVertexForDrag(paperPoint: Vec2, pixelsPerMm: Float): Vertex? {
    val doc = _uiState.value.document ?: return null
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
    val vertexHitMm = (24.0 / scale).coerceIn(2.5, 20.0)
    return GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
  }

  fun selectAt(paperPoint: Vec2, pixelsPerMm: Float) {
    val state = _uiState.value
    val doc = state.document ?: return
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
    val vertexHitMm = (22.0 / scale).coerceIn(2.0, 18.0)
    val edgeHitMm = (18.0 / scale).coerceIn(2.0, 14.0)

    val hitVertex = GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
    if (hitVertex != null) {
      _uiState.update { s ->
        val nextVertices =
          if (s.multiSelectEnabled) {
            if (hitVertex.id in s.selection.selectedVertexIds) s.selection.selectedVertexIds - hitVertex.id
            else s.selection.selectedVertexIds + hitVertex.id
          } else {
            setOf(hitVertex.id)
          }
        s.copy(
          selection =
            if (s.multiSelectEnabled) s.selection.copy(selectedVertexIds = nextVertices)
            else SelectionState(selectedVertexIds = nextVertices),
          cursorPaperPos = hitVertex.position,
        )
      }
      return
    }

    val hitEdge = GeometryEngine.findNearestEdge(doc, paperPoint, edgeHitMm)
    if (hitEdge != null) {
      _uiState.update { s ->
        val nextEdges =
          if (s.multiSelectEnabled) {
            if (hitEdge.id in s.selection.selectedEdgeIds) s.selection.selectedEdgeIds - hitEdge.id
            else s.selection.selectedEdgeIds + hitEdge.id
          } else {
            setOf(hitEdge.id)
          }
        s.copy(
          selection =
            if (s.multiSelectEnabled) s.selection.copy(selectedEdgeIds = nextEdges)
            else SelectionState(selectedEdgeIds = nextEdges),
          activeAssignment = hitEdge.assignment,
        )
      }
      return
    }

    val hitCircle = GeometryEngine.findNearestCircle(doc, paperPoint, edgeHitMm)
    if (hitCircle != null) {
      _uiState.update { s ->
        val nextCircles =
          if (s.multiSelectEnabled) {
            if (hitCircle.id in s.selection.selectedCircleIds) s.selection.selectedCircleIds - hitCircle.id
            else s.selection.selectedCircleIds + hitCircle.id
          } else {
            setOf(hitCircle.id)
          }
        s.copy(
          selection =
            if (s.multiSelectEnabled) s.selection.copy(selectedCircleIds = nextCircles)
            else SelectionState(selectedCircleIds = nextCircles),
        )
      }
      return
    }

    val hitArc = GeometryEngine.findNearestArc(doc, paperPoint, edgeHitMm)
    if (hitArc != null) {
      _uiState.update { s ->
        val nextArcs =
          if (s.multiSelectEnabled) {
            if (hitArc.id in s.selection.selectedArcIds) s.selection.selectedArcIds - hitArc.id
            else s.selection.selectedArcIds + hitArc.id
          } else {
            setOf(hitArc.id)
          }
        s.copy(
          selection =
            if (s.multiSelectEnabled) s.selection.copy(selectedArcIds = nextArcs)
            else SelectionState(selectedArcIds = nextArcs),
        )
      }
      return
    }

    if (!state.multiSelectEnabled) {
      _uiState.update { it.copy(selection = SelectionState()) }
    }
  }

  private fun selectWithinBox(cornerA: Vec2, cornerB: Vec2) {
    val doc = _uiState.value.document ?: return
    val minX = min(cornerA.x, cornerB.x)
    val maxX = max(cornerA.x, cornerB.x)
    val minY = min(cornerA.y, cornerB.y)
    val maxY = max(cornerA.y, cornerB.y)
    if (maxX - minX < 2.0 && maxY - minY < 2.0) return

    val selectedVerts =
      doc.vertices
        .filter { it.position.x in minX..maxX && it.position.y in minY..maxY }
        .map { it.id }
        .toSet()
    val vMap = doc.vertices.associateBy { it.id }
    val selectedEdges =
      doc.edges
        .filter { e ->
          val a = vMap[e.startVertexId]?.position
          val b = vMap[e.endVertexId]?.position
          a != null && b != null &&
            a.x in minX..maxX && a.y in minY..maxY &&
            b.x in minX..maxX && b.y in minY..maxY
        }
        .map { it.id }
        .toSet()
    val selectedCircles =
      doc.circles
        .filter { it.center.x in minX..maxX && it.center.y in minY..maxY }
        .map { it.id }
        .toSet()

    _uiState.update { s ->
      if (s.multiSelectEnabled) {
        s.copy(
          selection =
            SelectionState(
              selectedVertexIds = s.selection.selectedVertexIds + selectedVerts,
              selectedEdgeIds = s.selection.selectedEdgeIds + selectedEdges,
              selectedCircleIds = s.selection.selectedCircleIds + selectedCircles,
              selectedArcIds = s.selection.selectedArcIds,
            )
        )
      } else {
        s.copy(
          selection =
            SelectionState(
              selectedVertexIds = selectedVerts,
              selectedEdgeIds = selectedEdges,
              selectedCircleIds = selectedCircles,
            )
        )
      }
    }
  }

  fun selectAllInteriorGeometry() {
    val doc = _uiState.value.document ?: return
    val nonBoundaryEdges =
      doc.edges.filter { it.assignment != FoldAssignment.BOUNDARY }.map { it.id }.toSet()
    val nonBoundaryVerts =
      doc.edges
        .filter { it.assignment != FoldAssignment.BOUNDARY }
        .flatMap { listOf(it.startVertexId, it.endVertexId) }
        .toSet()
    _uiState.update {
      it.copy(
        selection =
          SelectionState(
            selectedVertexIds = nonBoundaryVerts,
            selectedEdgeIds = nonBoundaryEdges,
            selectedCircleIds = doc.circles.map { c -> c.id }.toSet(),
            selectedArcIds = doc.arcs.map { a -> a.id }.toSet(),
          )
      )
    }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selection = SelectionState()) }
  }

  private fun buildMoveOffsetsForSelection(
    doc: CreasePatternDocument?,
    selection: SelectionState,
    delta: Vec2,
  ): Map<String, Vec2> {
    if (doc == null) return emptyMap()
    val edgeVertIds =
      doc.edges
        .filter { it.id in selection.selectedEdgeIds }
        .flatMap { listOf(it.startVertexId, it.endVertexId) }
    val allIds = (selection.selectedVertexIds + edgeVertIds).toSet()
    val map = HashMap<String, Vec2>()
    for (v in doc.vertices) {
      if (v.id in allIds && !v.isLocked) {
        map[v.id] = v.position + delta
      }
    }
    return map
  }

  private fun commitDraggedVertices(doc: CreasePatternDocument, offsets: Map<String, Vec2>) {
    if (offsets.size == 1) {
      val (id, finalPos) = offsets.entries.first()
      val original = doc.vertexById(id)?.position ?: return
      if (GeometryEngine.distance(original, finalPos) > 0.05) {
        executeCommand(MoveVertexCommand(id, original, finalPos))
      }
      return
    }
    val updatedVertices =
      doc.vertices.map { v ->
        val next = offsets[v.id]
        if (next != null && !v.isLocked) v.copy(position = next) else v
      }
    val updated = doc.copy(vertices = updatedVertices, updatedAt = System.currentTimeMillis())
    commitDocumentSnapshot("Move selection", doc, updated)
  }

  fun eraseAt(paperPoint: Vec2, pixelsPerMm: Float) {
    val doc = _uiState.value.document ?: return
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.4)
    val edgeHitMm = (18.0 / scale).coerceIn(2.0, 14.0)
    val vertexHitMm = (20.0 / scale).coerceIn(2.0, 16.0)

    val hitEdge = GeometryEngine.findNearestEdge(doc, paperPoint, edgeHitMm)
    if (hitEdge != null) {
      executeCommand(DeleteEdgeCommand(hitEdge))
      _uiState.update {
        it.copy(selection = it.selection.copy(selectedEdgeIds = it.selection.selectedEdgeIds - hitEdge.id))
      }
      return
    }

    val hitCircle = GeometryEngine.findNearestCircle(doc, paperPoint, edgeHitMm)
    if (hitCircle != null) {
      val updated = doc.copy(circles = doc.circles.filterNot { it.id == hitCircle.id })
      commitDocumentSnapshot("Delete circle", doc, updated)
      return
    }

    val hitArc = GeometryEngine.findNearestArc(doc, paperPoint, edgeHitMm)
    if (hitArc != null) {
      val updated = doc.copy(arcs = doc.arcs.filterNot { it.id == hitArc.id })
      commitDocumentSnapshot("Delete arc", doc, updated)
      return
    }

    val hitVertex = GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
    if (hitVertex != null) {
      val connectedEdges =
        doc.edges.filter { it.startVertexId == hitVertex.id || it.endVertexId == hitVertex.id }
      executeCommand(DeleteVertexCommand(hitVertex, connectedEdges))
      _uiState.update {
        it.copy(selection = it.selection.copy(selectedVertexIds = it.selection.selectedVertexIds - hitVertex.id))
      }
    }
  }

  fun deleteSelection() {
    val state = _uiState.value
    val doc = state.document ?: return
    if (!state.selection.hasSelection) return

    if (state.selection.singleEdgeId != null &&
      state.selection.selectedCircleIds.isEmpty() &&
      state.selection.selectedArcIds.isEmpty()
    ) {
      val edge = doc.edges.firstOrNull { it.id == state.selection.singleEdgeId } ?: return
      executeCommand(DeleteEdgeCommand(edge))
      _uiState.update { it.copy(selection = SelectionState()) }
      return
    }

    if (state.selection.singleVertexId != null &&
      state.selection.selectedCircleIds.isEmpty() &&
      state.selection.selectedArcIds.isEmpty()
    ) {
      val v = doc.vertexById(state.selection.singleVertexId!!) ?: return
      val connected = doc.edges.filter { it.startVertexId == v.id || it.endVertexId == v.id }
      executeCommand(DeleteVertexCommand(v, connected))
      _uiState.update { it.copy(selection = SelectionState()) }
      return
    }

    val delVerts = state.selection.selectedVertexIds
    val delEdges = state.selection.selectedEdgeIds
    val remainingVertices = doc.vertices.filterNot { it.id in delVerts }
    val validVertIds = remainingVertices.map { it.id }.toSet()
    val remainingEdges =
      doc.edges.filter {
        it.id !in delEdges && it.startVertexId in validVertIds && it.endVertexId in validVertIds
      }
    val remainingCircles = doc.circles.filterNot { it.id in state.selection.selectedCircleIds }
    val remainingArcs = doc.arcs.filterNot { it.id in state.selection.selectedArcIds }
    val updated =
      doc.copy(
        vertices = remainingVertices,
        edges = remainingEdges,
        circles = remainingCircles,
        arcs = remainingArcs,
        updatedAt = System.currentTimeMillis(),
      )
    commitDocumentSnapshot("Delete selection", doc, updated)
    _uiState.update { it.copy(selection = SelectionState()) }
  }

  // --- Selection & Geometry Transformations ---

  fun dismissDialog() {
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun openTransformDialog(kind: DialogKind) {
    _uiState.update { it.copy(pendingDialog = PendingDialogRequest(kind)) }
  }

  fun createCircleWithRadius(center: Vec2, radiusMm: Double) {
    val state = _uiState.value
    val circle = ConstructionEngine.circleFromCenterAndRadius(center, radiusMm, state.activeAssignment)
    if (circle != null) executeCommand(AddCircleCommand(circle))
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun moveSelectedVertexTo(xMm: Double, yMm: Double) {
    val state = _uiState.value
    val doc = state.document ?: return
    val vertexId = state.selection.singleVertexId ?: return
    val vertex = doc.vertexById(vertexId) ?: return
    val target = Vec2(xMm, yMm)
    if (GeometryEngine.distance(vertex.position, target) > GeometryEngine.EPSILON) {
      executeCommand(MoveVertexCommand(vertex.id, vertex.position, target))
      _uiState.update { it.copy(cursorPaperPos = target) }
    }
  }

  fun setSelectedEdgeLength(newLengthMm: Double) {
    val state = _uiState.value
    val doc = state.document ?: return
    val edgeId = state.selection.singleEdgeId ?: return
    val updated = CreaseEditingEngine.setEdgeLength(doc, edgeId, newLengthMm)
    if (updated != doc) commitDocumentSnapshot("Edit edge length", doc, updated)
  }

  fun setSelectedEdgeAngle(newAngleDeg: Double) {
    val state = _uiState.value
    val doc = state.document ?: return
    val edgeId = state.selection.singleEdgeId ?: return
    val updated = CreaseEditingEngine.setEdgeAngle(doc, edgeId, newAngleDeg)
    if (updated != doc) commitDocumentSnapshot("Edit edge angle", doc, updated)
  }

  fun flipSelectedEdgeMountainValley() {
    val state = _uiState.value
    val doc = state.document ?: return
    val edgeId = state.selection.singleEdgeId ?: return
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return
    val next =
      when (edge.assignment) {
        FoldAssignment.MOUNTAIN -> FoldAssignment.VALLEY
        FoldAssignment.VALLEY -> FoldAssignment.MOUNTAIN
        else -> FoldAssignment.MOUNTAIN
      }
    executeCommand(ChangeEdgeAssignmentCommand(edge.id, edge.assignment, next))
    _uiState.update { it.copy(activeAssignment = next) }
  }

  fun splitSelectedEdgeAtMidpoint() {
    val state = _uiState.value
    val doc = state.document ?: return
    val edgeId = state.selection.singleEdgeId ?: return
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return
    val a = doc.vertexById(edge.startVertexId)?.position ?: return
    val b = doc.vertexById(edge.endVertexId)?.position ?: return
    val mid = GeometryEngine.midpoint(a, b)
    val updated =
      CreaseEditingEngine.splitEdgeAtPoint(doc, edgeId, mid, state.geometrySettings.geometryToleranceMm)
    if (updated != doc) {
      commitDocumentSnapshot("Split edge at midpoint", doc, updated)
      _uiState.update { it.copy(selection = SelectionState()) }
    }
  }

  fun joinTwoSelectedEdges() {
    val state = _uiState.value
    val doc = state.document ?: return
    if (state.selection.selectedEdgeIds.size != 2) return
    val ids = state.selection.selectedEdgeIds.toList()
    val updated =
      CreaseEditingEngine.joinCompatibleEdges(
        doc,
        ids[0],
        ids[1],
        state.geometrySettings.geometryToleranceMm.coerceAtLeast(0.05),
      )
    if (updated != doc) {
      commitDocumentSnapshot("Join compatible edges", doc, updated)
      _uiState.update { it.copy(selection = SelectionState()) }
    } else {
      _uiState.update {
        it.copy(statusBannerMessage = "Selected edges must share a degree-2 vertex, be collinear, and have the same fold type")
      }
    }
  }

  fun splitAllIntersections() {
    val state = _uiState.value
    val doc = state.document ?: return
    val updated =
      CreaseEditingEngine.splitAllEdgesAtIntersections(doc, state.geometrySettings.geometryToleranceMm)
    if (updated != doc) {
      commitDocumentSnapshot("Split all intersections", doc, updated)
      _uiState.update { it.copy(statusBannerMessage = "Split edges at intersections") }
    } else {
      _uiState.update { it.copy(statusBannerMessage = "No unsplit interior intersections found") }
    }
  }

  fun mergeNearbyVertices() {
    val state = _uiState.value
    val doc = state.document ?: return
    val tol = state.geometrySettings.geometryToleranceMm.coerceAtLeast(0.1)
    val updated = CreaseEditingEngine.mergeNearbyVertices(doc, tol)
    if (updated != doc) {
      commitDocumentSnapshot("Merge nearby vertices", doc, updated)
      _uiState.update { it.copy(selection = SelectionState(), statusBannerMessage = "Merged nearby vertices") }
    } else {
      _uiState.update { it.copy(statusBannerMessage = "No vertices within merge tolerance") }
    }
  }

  fun moveSelectionByDelta(dx: Double, dy: Double, duplicate: Boolean = false) {
    val state = _uiState.value
    val doc = state.document ?: return
    if (!state.selection.hasSelection) return
    val updated =
      TransformEngine.transformSelection(
        doc = doc,
        selectedVertexIds = state.selection.selectedVertexIds,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        duplicate = duplicate,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
      ) { pt -> TransformEngine.translate(pt, Vec2(dx, dy)) }
    commitDocumentSnapshot(if (duplicate) "Duplicate selection" else "Translate selection", doc, updated)
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun duplicateSelectionOffset() {
    val doc = _uiState.value.document ?: return
    val step = doc.paper.gridSpacingMm.coerceAtLeast(10.0)
    moveSelectionByDelta(step, step, duplicate = true)
  }

  fun rotateSelection(angleDeg: Double, centerOverride: Vec2? = null, duplicate: Boolean = false) {
    val state = _uiState.value
    val doc = state.document ?: return
    if (!state.selection.hasSelection) return
    val center =
      centerOverride
        ?: TransformEngine.computeSelectionCentroid(
          doc,
          state.selection.selectedVertexIds,
          state.selection.selectedEdgeIds,
          state.selection.selectedCircleIds,
          state.selection.selectedArcIds,
        )
    val updated =
      TransformEngine.transformSelection(
        doc = doc,
        selectedVertexIds = state.selection.selectedVertexIds,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        duplicate = duplicate,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
        rotationDeltaDegForArcs = angleDeg,
      ) { pt -> TransformEngine.rotate(pt, center, angleDeg) }
    commitDocumentSnapshot("Rotate selection ${angleDeg}°", doc, updated)
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun scaleSelection(factor: Double, centerOverride: Vec2? = null, duplicate: Boolean = false) {
    val state = _uiState.value
    val doc = state.document ?: return
    if (!state.selection.hasSelection || factor <= 1e-3) return
    val center =
      centerOverride
        ?: TransformEngine.computeSelectionCentroid(
          doc,
          state.selection.selectedVertexIds,
          state.selection.selectedEdgeIds,
          state.selection.selectedCircleIds,
          state.selection.selectedArcIds,
        )
    val updated =
      TransformEngine.transformSelection(
        doc = doc,
        selectedVertexIds = state.selection.selectedVertexIds,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        duplicate = duplicate,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
        scaleFactorForCircles = factor,
      ) { pt -> TransformEngine.scale(pt, center, factor) }
    commitDocumentSnapshot("Scale selection ×$factor", doc, updated)
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun flipSelectionHorizontal() {
    val state = _uiState.value
    val doc = state.document ?: return
    val axisX =
      if (state.selection.hasSelection) {
        TransformEngine.computeSelectionCentroid(
          doc,
          state.selection.selectedVertexIds,
          state.selection.selectedEdgeIds,
          state.selection.selectedCircleIds,
          state.selection.selectedArcIds,
        ).x
      } else {
        doc.paper.widthMm * 0.5
      }
    val selVerts =
      if (state.selection.hasSelection) state.selection.selectedVertexIds
      else doc.vertices.map { it.id }.toSet()
    val updated =
      TransformEngine.transformSelection(
        doc = doc,
        selectedVertexIds = selVerts,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        duplicate = false,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
        isReflectionForArcs = true,
      ) { pt -> TransformEngine.flipHorizontal(pt, axisX) }
    commitDocumentSnapshot("Flip horizontally", doc, updated)
  }

  fun flipSelectionVertical() {
    val state = _uiState.value
    val doc = state.document ?: return
    val axisY =
      if (state.selection.hasSelection) {
        TransformEngine.computeSelectionCentroid(
          doc,
          state.selection.selectedVertexIds,
          state.selection.selectedEdgeIds,
          state.selection.selectedCircleIds,
          state.selection.selectedArcIds,
        ).y
      } else {
        doc.paper.heightMm * 0.5
      }
    val selVerts =
      if (state.selection.hasSelection) state.selection.selectedVertexIds
      else doc.vertices.map { it.id }.toSet()
    val updated =
      TransformEngine.transformSelection(
        doc = doc,
        selectedVertexIds = selVerts,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        duplicate = false,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
        isReflectionForArcs = true,
      ) { pt -> TransformEngine.flipVertical(pt, axisY) }
    commitDocumentSnapshot("Flip vertically", doc, updated)
  }

  fun beginCustomSymmetryAxisPick() {
    isPickingCustomSymmetryAxis = true
    _uiState.update {
      it.copy(
        pendingDialog = null,
        pickedPoints = emptyList(),
        statusBannerMessage = "Custom Symmetry: Tap first point on mirror axis",
      )
    }
  }

  fun applySymmetrySpec(spec: SymmetrySpec) {
    val state = _uiState.value
    val doc = state.document ?: return
    val updated =
      TransformEngine.applySymmetry(
        doc = doc,
        selectedVertexIds = state.selection.selectedVertexIds,
        selectedEdgeIds = state.selection.selectedEdgeIds,
        selectedCircleIds = state.selection.selectedCircleIds,
        selectedArcIds = state.selection.selectedArcIds,
        symmetry = spec,
        toleranceMm = state.geometrySettings.geometryToleranceMm,
      )
    commitDocumentSnapshot("Apply symmetry", doc, updated)
    _uiState.update { it.copy(pendingDialog = null) }
  }

  fun renameProject(newName: String) {
    val doc = _uiState.value.document ?: return
    val trimmed = newName.trim()
    if (trimmed.isEmpty() || trimmed == doc.name) return
    val updated = doc.copy(name = trimmed, updatedAt = System.currentTimeMillis())
    _uiState.update { it.copy(document = updated, isDirty = true) }
    scheduleAutoSave()
  }

  fun updatePaperSettings(widthMm: Double, heightMm: Double, gridSpacingMm: Double) {
    val doc = _uiState.value.document ?: return
    val newPaper =
      PaperSpec(
        widthMm = widthMm.coerceIn(10.0, 2000.0),
        heightMm = heightMm.coerceIn(10.0, 2000.0),
        gridSpacingMm = gridSpacingMm.coerceIn(1.0, 500.0),
      )
    if (newPaper != doc.paper) {
      executeCommand(UpdatePaperSpecCommand(doc.paper, newPaper))
    }
  }

  fun undo() {
    val doc = _uiState.value.document ?: return
    if (!historyManager.canUndo) return
    val updated = historyManager.undo(doc)
    val intersections =
      IntersectionEngine.computeAllIntersections(updated, _uiState.value.geometrySettings.geometryToleranceMm)
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
        selection = SelectionState(),
        pickedPoints = emptyList(),
        pickedVertexIds = emptyList(),
        previewShape = null,
        cachedIntersections = intersections,
      )
    }
    scheduleAutoSave()
  }

  fun redo() {
    val doc = _uiState.value.document ?: return
    if (!historyManager.canRedo) return
    val updated = historyManager.redo(doc)
    val intersections =
      IntersectionEngine.computeAllIntersections(updated, _uiState.value.geometrySettings.geometryToleranceMm)
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
        selection = SelectionState(),
        pickedPoints = emptyList(),
        pickedVertexIds = emptyList(),
        previewShape = null,
        cachedIntersections = intersections,
      )
    }
    scheduleAutoSave()
  }

  fun saveNow(onSaved: (() -> Unit)? = null) {
    autoSaveJob?.cancel()
    val doc = _uiState.value.document
    if (doc == null) {
      onSaved?.invoke()
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true) }
      val toSave = doc.copy(updatedAt = System.currentTimeMillis())
      projectRepository.saveProject(toSave)
      _uiState.update {
        it.copy(
          document = toSave,
          isDirty = false,
          isSaving = false,
          lastSavedAt = toSave.updatedAt,
        )
      }
      onSaved?.invoke()
    }
  }

  private fun commitDocumentSnapshot(
    description: String,
    before: CreasePatternDocument,
    after: CreasePatternDocument,
  ) {
    executeCommand(DocumentSnapshotCommand(description, before, after))
  }

  private fun executeCommand(command: EditorCommand) {
    val doc = _uiState.value.document ?: return
    val updated = historyManager.execute(doc, command)
    val intersections =
      IntersectionEngine.computeAllIntersections(updated, _uiState.value.geometrySettings.geometryToleranceMm)
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
        cachedIntersections = intersections,
      )
    }
    scheduleAutoSave()
  }

  private fun scheduleAutoSave() {
    autoSaveJob?.cancel()
    autoSaveJob =
      viewModelScope.launch {
        delay(1200L)
        val doc = _uiState.value.document ?: return@launch
        _uiState.update { it.copy(isSaving = true) }
        val savedDoc = doc.copy(updatedAt = System.currentTimeMillis())
        projectRepository.saveProject(savedDoc)
        _uiState.update {
          it.copy(
            document = savedDoc,
            isDirty = false,
            isSaving = false,
            lastSavedAt = savedDoc.updatedAt,
          )
        }
      }
  }

  override fun onCleared() {
    super.onCleared()
    autoSaveJob?.cancel()
  }

  companion object {
    fun factory(
      projectId: String,
      projectRepository: ProjectRepository,
      preferencesRepository: StudioPreferencesRepository,
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return EditorViewModel(projectId, projectRepository, preferencesRepository) as T
        }
      }
  }
}
