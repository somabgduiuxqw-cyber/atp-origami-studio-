package com.example.features.editor

import com.example.core.model.CreasePatternDocument
import com.example.core.model.FoldAssignment
import com.example.core.model.Vec2
import com.example.core.snapping.SnapKind

enum class EditorTool(val label: String) {
  SELECT("Select"),
  PAN("Pan"),
  POINT("Point"),
  LINE("Line"),
  ERASER("Erase"),
}

data class SelectionState(
  val selectedVertexId: String? = null,
  val selectedEdgeId: String? = null,
) {
  val hasSelection: Boolean
    get() = selectedVertexId != null || selectedEdgeId != null
}

data class EditorViewportState(
  val zoom: Float = 1f,
  val panOffsetX: Float = 0f,
  val panOffsetY: Float = 0f,
)

data class PendingLinePreview(
  val startPosition: Vec2,
  val startVertexId: String?,
  val currentEndPosition: Vec2? = null,
  val currentEndVertexId: String? = null,
)

data class SnapIndicator(
  val position: Vec2,
  val kind: SnapKind,
)

data class DragMoveVertexPreview(
  val vertexId: String,
  val originalPosition: Vec2,
  val currentPosition: Vec2,
)

data class EditorUiState(
  val isLoading: Boolean = true,
  val loadError: String? = null,
  val document: CreasePatternDocument? = null,
  val activeTool: EditorTool = EditorTool.LINE,
  val activeAssignment: FoldAssignment = FoldAssignment.MOUNTAIN,
  val selection: SelectionState = SelectionState(),
  val viewport: EditorViewportState = EditorViewportState(),
  val showGrid: Boolean = true,
  val snapToGrid: Boolean = true,
  val snapToVertex: Boolean = true,
  val showCoordinates: Boolean = true,
  val isToolPanelExpanded: Boolean = true,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val isDirty: Boolean = false,
  val isSaving: Boolean = false,
  val lastSavedAt: Long? = null,
  val cursorPaperPos: Vec2? = null,
  val snapIndicator: SnapIndicator? = null,
  val pendingLinePreview: PendingLinePreview? = null,
  val dragVertexPreview: DragMoveVertexPreview? = null,
)
