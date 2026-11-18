package com.example.features.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.geometry.GeometryEngine
import com.example.core.history.AddCreaseCommand
import com.example.core.history.AddVertexCommand
import com.example.core.history.ChangeEdgeAssignmentCommand
import com.example.core.history.DeleteEdgeCommand
import com.example.core.history.DeleteVertexCommand
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
import com.example.core.snapping.SnapConfig
import com.example.core.snapping.SnapEngine
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

class EditorViewModel(
  private val projectId: String,
  private val projectRepository: ProjectRepository,
  private val preferencesRepository: StudioPreferencesRepository,
) : ViewModel() {

  private val historyManager = HistoryManager()
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  private var autoSaveJob: Job? = null

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
      _uiState.update {
        it.copy(
          isLoading = false,
          document = doc,
          showGrid = prefs.defaultShowGrid,
          snapToGrid = prefs.defaultSnapToGrid,
          snapToVertex = prefs.defaultSnapToVertex,
          showCoordinates = prefs.showCoordinateHud,
          lastSavedAt = doc.updatedAt,
          canUndo = false,
          canRedo = false,
          isDirty = false,
        )
      }
    }
  }

  fun selectTool(tool: EditorTool) {
    _uiState.update {
      it.copy(
        activeTool = tool,
        pendingLinePreview = null,
        dragVertexPreview = null,
        snapIndicator = null,
      )
    }
  }

  fun selectAssignment(assignment: FoldAssignment) {
    val state = _uiState.value
    val selectedEdgeId = state.selection.selectedEdgeId
    val doc = state.document
    if (selectedEdgeId != null && doc != null) {
      val edge = doc.edges.firstOrNull { it.id == selectedEdgeId }
      if (edge != null && edge.assignment != assignment) {
        executeCommand(
          ChangeEdgeAssignmentCommand(
            edgeId = edge.id,
            oldAssignment = edge.assignment,
            newAssignment = assignment,
          )
        )
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

  fun toggleSnapToGrid() {
    _uiState.update { it.copy(snapToGrid = !it.snapToGrid) }
  }

  fun toggleSnapToVertex() {
    _uiState.update { it.copy(snapToVertex = !it.snapToVertex) }
  }

  fun toggleShowCoordinates() {
    _uiState.update { it.copy(showCoordinates = !it.showCoordinates) }
  }

  fun zoomToFit() {
    _uiState.update { it.copy(viewport = EditorViewportState()) }
  }

  fun applyViewportTransform(zoomDelta: Float, panDeltaX: Float, panDeltaY: Float) {
    _uiState.update { state ->
      val newZoom = (state.viewport.zoom * zoomDelta).coerceIn(0.25f, 16f)
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

  fun snapPoint(rawPaperPoint: Vec2, pixelsPerMm: Float): SnapResult {
    val state = _uiState.value
    val doc = state.document ?: return SnapResult(rawPaperPoint, com.example.core.snapping.SnapKind.NONE)
    val safeScale = pixelsPerMm.toDouble().coerceAtLeast(0.5)
    val vertexRadiusMm = (22.0 / safeScale).coerceIn(2.0, 18.0)
    val gridRadiusMm = (16.0 / safeScale).coerceIn(1.5, 14.0)
    return SnapEngine.resolveSnap(
      rawPaperPoint = rawPaperPoint,
      document = doc,
      config =
        SnapConfig(
          snapToGrid = state.snapToGrid,
          snapToVertex = state.snapToVertex,
          gridSpacingMm = doc.paper.gridSpacingMm,
          vertexSnapRadiusMm = vertexRadiusMm,
          gridSnapRadiusMm = gridRadiusMm,
        ),
    )
  }

  fun updateHoverSnap(rawPaperPoint: Vec2?, snapResult: SnapResult?) {
    _uiState.update {
      it.copy(
        cursorPaperPos = snapResult?.position ?: rawPaperPoint,
        snapIndicator =
          if (snapResult != null && snapResult.kind != com.example.core.snapping.SnapKind.NONE) {
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
        dragVertexPreview = null,
      )
    }
  }

  fun placePoint(snapResult: SnapResult) {
    val doc = _uiState.value.document ?: return
    val existing =
      if (snapResult.snappedVertexId != null) {
        doc.vertexById(snapResult.snappedVertexId)
      } else {
        GeometryEngine.findDuplicateVertex(doc.vertices, snapResult.position)
      }
    if (existing != null) {
      _uiState.update {
        it.copy(
          selection = SelectionState(selectedVertexId = existing.id),
          cursorPaperPos = existing.position,
        )
      }
      return
    }
    val vertex = Vertex(id = GeometryEngine.newId(), position = snapResult.position)
    executeCommand(AddVertexCommand(vertex))
    _uiState.update {
      it.copy(
        selection = SelectionState(selectedVertexId = vertex.id),
        cursorPaperPos = vertex.position,
      )
    }
  }

  fun beginOrUpdateLineDrag(
    startSnap: SnapResult,
    currentEndSnap: SnapResult,
  ) {
    _uiState.update {
      it.copy(
        pendingLinePreview =
          PendingLinePreview(
            startPosition = startSnap.position,
            startVertexId = startSnap.snappedVertexId,
            currentEndPosition = currentEndSnap.position,
            currentEndVertexId = currentEndSnap.snappedVertexId,
          ),
        cursorPaperPos = currentEndSnap.position,
        snapIndicator =
          if (currentEndSnap.kind != com.example.core.snapping.SnapKind.NONE) {
            SnapIndicator(currentEndSnap.position, currentEndSnap.kind)
          } else {
            null
          },
      )
    }
  }

  fun handleLineTap(snapResult: SnapResult) {
    val currentPending = _uiState.value.pendingLinePreview
    if (currentPending == null) {
      _uiState.update {
        it.copy(
          pendingLinePreview =
            PendingLinePreview(
              startPosition = snapResult.position,
              startVertexId = snapResult.snappedVertexId,
              currentEndPosition = null,
              currentEndVertexId = null,
            ),
          cursorPaperPos = snapResult.position,
        )
      }
    } else {
      commitLineSegment(
        startPos = currentPending.startPosition,
        startVertexId = currentPending.startVertexId,
        endPos = snapResult.position,
        endVertexId = snapResult.snappedVertexId,
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
    if (GeometryEngine.distance(startPos, endPos) < 0.5) {
      _uiState.update { it.copy(pendingLinePreview = null, snapIndicator = null) }
      return
    }

    val existingStart =
      startVertexId?.let { doc.vertexById(it) }
        ?: GeometryEngine.findDuplicateVertex(doc.vertices, startPos)
    val startVertex = existingStart ?: Vertex(id = GeometryEngine.newId(), position = startPos)

    val existingEnd =
      endVertexId?.let { doc.vertexById(it) }
        ?: GeometryEngine.findDuplicateVertex(doc.vertices, endPos)
    val endVertex =
      if (existingEnd != null && existingEnd.id != startVertex.id) {
        existingEnd
      } else {
        Vertex(id = GeometryEngine.newId(), position = endPos)
      }

    if (startVertex.id == endVertex.id) {
      _uiState.update { it.copy(pendingLinePreview = null, snapIndicator = null) }
      return
    }

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
      _uiState.update {
        it.copy(
          pendingLinePreview = null,
          snapIndicator = null,
          selection = SelectionState(selectedEdgeId = duplicateEdge.id),
        )
      }
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
    _uiState.update {
      it.copy(
        pendingLinePreview = null,
        snapIndicator = null,
        selection = SelectionState(selectedEdgeId = newEdge.id),
      )
    }
  }

  fun selectAt(paperPoint: Vec2, pixelsPerMm: Float) {
    val doc = _uiState.value.document ?: return
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.5)
    val vertexHitMm = (22.0 / scale).coerceIn(2.0, 18.0)
    val edgeHitMm = (18.0 / scale).coerceIn(2.0, 14.0)

    val hitVertex = GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
    if (hitVertex != null) {
      _uiState.update {
        it.copy(
          selection = SelectionState(selectedVertexId = hitVertex.id),
          cursorPaperPos = hitVertex.position,
        )
      }
      return
    }

    val hitEdge = GeometryEngine.findNearestEdge(doc, paperPoint, edgeHitMm)
    if (hitEdge != null) {
      _uiState.update {
        it.copy(
          selection = SelectionState(selectedEdgeId = hitEdge.id),
          activeAssignment = hitEdge.assignment,
        )
      }
      return
    }

    _uiState.update { it.copy(selection = SelectionState()) }
  }

  fun findHitVertexForDrag(paperPoint: Vec2, pixelsPerMm: Float): Vertex? {
    val doc = _uiState.value.document ?: return null
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.5)
    val vertexHitMm = (24.0 / scale).coerceIn(2.5, 20.0)
    return GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
  }

  fun updateDragVertexPreview(vertex: Vertex, currentPos: Vec2, snapResult: SnapResult) {
    _uiState.update {
      it.copy(
        selection = SelectionState(selectedVertexId = vertex.id),
        dragVertexPreview =
          DragMoveVertexPreview(
            vertexId = vertex.id,
            originalPosition = vertex.position,
            currentPosition = snapResult.position,
          ),
        cursorPaperPos = snapResult.position,
        snapIndicator =
          if (snapResult.kind != com.example.core.snapping.SnapKind.NONE) {
            SnapIndicator(snapResult.position, snapResult.kind)
          } else {
            null
          },
      )
    }
  }

  fun commitDragVertexMove(vertexId: String, originalPosition: Vec2, finalPosition: Vec2) {
    if (GeometryEngine.distance(originalPosition, finalPosition) > 0.05) {
      executeCommand(
        MoveVertexCommand(
          vertexId = vertexId,
          oldPosition = originalPosition,
          newPosition = finalPosition,
        )
      )
    }
    _uiState.update {
      it.copy(
        dragVertexPreview = null,
        snapIndicator = null,
        cursorPaperPos = finalPosition,
      )
    }
  }

  fun moveSelectedVertexTo(xMm: Double, yMm: Double) {
    val state = _uiState.value
    val doc = state.document ?: return
    val vertexId = state.selection.selectedVertexId ?: return
    val vertex = doc.vertexById(vertexId) ?: return
    val target = Vec2(xMm, yMm)
    if (GeometryEngine.distance(vertex.position, target) > GeometryEngine.EPSILON) {
      executeCommand(MoveVertexCommand(vertex.id, vertex.position, target))
      _uiState.update { it.copy(cursorPaperPos = target) }
    }
  }

  fun eraseAt(paperPoint: Vec2, pixelsPerMm: Float) {
    val doc = _uiState.value.document ?: return
    val scale = pixelsPerMm.toDouble().coerceAtLeast(0.5)
    val edgeHitMm = (18.0 / scale).coerceIn(2.0, 14.0)
    val vertexHitMm = (20.0 / scale).coerceIn(2.0, 16.0)

    val hitEdge = GeometryEngine.findNearestEdge(doc, paperPoint, edgeHitMm)
    if (hitEdge != null) {
      executeCommand(DeleteEdgeCommand(hitEdge))
      _uiState.update {
        if (it.selection.selectedEdgeId == hitEdge.id) {
          it.copy(selection = SelectionState())
        } else {
          it
        }
      }
      return
    }

    val hitVertex = GeometryEngine.findNearestVertex(doc, paperPoint, vertexHitMm)
    if (hitVertex != null) {
      val connectedEdges =
        doc.edges.filter { it.startVertexId == hitVertex.id || it.endVertexId == hitVertex.id }
      executeCommand(DeleteVertexCommand(hitVertex, connectedEdges))
      _uiState.update {
        if (it.selection.selectedVertexId == hitVertex.id) {
          it.copy(selection = SelectionState())
        } else {
          it
        }
      }
    }
  }

  fun deleteSelection() {
    val state = _uiState.value
    val doc = state.document ?: return
    val selectedEdgeId = state.selection.selectedEdgeId
    if (selectedEdgeId != null) {
      val edge = doc.edges.firstOrNull { it.id == selectedEdgeId } ?: return
      executeCommand(DeleteEdgeCommand(edge))
      _uiState.update { it.copy(selection = SelectionState()) }
      return
    }

    val selectedVertexId = state.selection.selectedVertexId
    if (selectedVertexId != null) {
      val vertex = doc.vertexById(selectedVertexId) ?: return
      val connected =
        doc.edges.filter { it.startVertexId == vertex.id || it.endVertexId == vertex.id }
      executeCommand(DeleteVertexCommand(vertex, connected))
      _uiState.update { it.copy(selection = SelectionState()) }
    }
  }

  fun flipSelectedEdgeMountainValley() {
    val state = _uiState.value
    val doc = state.document ?: return
    val selectedEdgeId = state.selection.selectedEdgeId ?: return
    val edge = doc.edges.firstOrNull { it.id == selectedEdgeId } ?: return
    val next =
      when (edge.assignment) {
        FoldAssignment.MOUNTAIN -> FoldAssignment.VALLEY
        FoldAssignment.VALLEY -> FoldAssignment.MOUNTAIN
        else -> FoldAssignment.MOUNTAIN
      }
    executeCommand(ChangeEdgeAssignmentCommand(edge.id, edge.assignment, next))
    _uiState.update { it.copy(activeAssignment = next) }
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
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
        selection = SelectionState(),
        pendingLinePreview = null,
      )
    }
    scheduleAutoSave()
  }

  fun redo() {
    val doc = _uiState.value.document ?: return
    if (!historyManager.canRedo) return
    val updated = historyManager.redo(doc)
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
        selection = SelectionState(),
        pendingLinePreview = null,
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

  private fun executeCommand(command: EditorCommand) {
    val doc = _uiState.value.document ?: return
    val updated = historyManager.execute(doc, command)
    _uiState.update {
      it.copy(
        document = updated,
        canUndo = historyManager.canUndo,
        canRedo = historyManager.canRedo,
        isDirty = true,
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
