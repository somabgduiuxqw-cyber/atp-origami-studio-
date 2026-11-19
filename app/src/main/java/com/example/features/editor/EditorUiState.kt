package com.example.features.editor

import com.example.core.model.CreasePatternDocument
import com.example.core.model.FoldAssignment
import com.example.core.model.GeometrySettings
import com.example.core.model.Vec2
import com.example.core.snapping.SnapConfig
import com.example.core.snapping.SnapKind

enum class ToolCategory(val label: String) {
  NAVIGATE("Select & Nav"),
  LINES("Lines"),
  CIRCLES("Circles & Arcs"),
  POLYGONS("Polygons"),
  EDIT("Edit & Transform"),
}

enum class EditorTool(
  val label: String,
  val category: ToolCategory,
) {
  // Navigate & Select
  SELECT("Select", ToolCategory.NAVIGATE),
  PAN("Pan", ToolCategory.NAVIGATE),
  POINT("Point", ToolCategory.NAVIGATE),
  ERASER("Erase", ToolCategory.NAVIGATE),

  // Line Construction
  LINE_SEGMENT("Segment", ToolCategory.LINES),
  RAY("Ray", ToolCategory.LINES),
  INFINITE_LINE("Inf Line", ToolCategory.LINES),
  PARALLEL_LINE("Parallel", ToolCategory.LINES),
  PERPENDICULAR_LINE("Perp", ToolCategory.LINES),
  ANGLE_BISECTOR("Bisector", ToolCategory.LINES),
  MIDPOINT("Midpoint", ToolCategory.LINES),

  // Circle & Arc Construction
  CIRCLE_CENTER_RADIUS("Center+R", ToolCategory.CIRCLES),
  CIRCLE_TWO_POINTS("2-Pt Circle", ToolCategory.CIRCLES),
  CIRCLE_THREE_POINTS("3-Pt Circle", ToolCategory.CIRCLES),
  CIRCLE_INTERSECTIONS("Circle X", ToolCategory.CIRCLES),
  ARC("Arc", ToolCategory.CIRCLES),

  // Polygons
  TRIANGLE("Triangle", ToolCategory.POLYGONS),
  RECTANGLE("Rectangle", ToolCategory.POLYGONS),
  REGULAR_POLYGON("N-gon", ToolCategory.POLYGONS),
  CUSTOM_POLYGON("Polygon", ToolCategory.POLYGONS),
  POLYGON_FROM_VERTICES("From Verts", ToolCategory.POLYGONS),

  // Edit & Transform
  MOVE("Move", ToolCategory.EDIT),
  ROTATE("Rotate", ToolCategory.EDIT),
  SCALE("Scale", ToolCategory.EDIT),
  MIRROR_LINE("Mirror Axis", ToolCategory.EDIT),
  SYMMETRY("Symmetry", ToolCategory.EDIT),
  SPLIT_EDGE("Split", ToolCategory.EDIT),
  EXTEND_LINE("Extend", ToolCategory.EDIT),
  TRIM_LINE("Trim", ToolCategory.EDIT),
  JOIN_EDGES("Join", ToolCategory.EDIT),
}

data class SelectionState(
  val selectedVertexIds: Set<String> = emptySet(),
  val selectedEdgeIds: Set<String> = emptySet(),
  val selectedCircleIds: Set<String> = emptySet(),
  val selectedArcIds: Set<String> = emptySet(),
) {
  val hasSelection: Boolean
    get() =
      selectedVertexIds.isNotEmpty() ||
        selectedEdgeIds.isNotEmpty() ||
        selectedCircleIds.isNotEmpty() ||
        selectedArcIds.isNotEmpty()

  val singleVertexId: String?
    get() = if (selectedVertexIds.size == 1 && selectedEdgeIds.isEmpty()) selectedVertexIds.first() else null

  val singleEdgeId: String?
    get() = if (selectedEdgeIds.size == 1 && selectedVertexIds.isEmpty()) selectedEdgeIds.first() else null
}

data class EditorViewportState(
  val zoom: Float = 1f,
  val panOffsetX: Float = 0f,
  val panOffsetY: Float = 0f,
)

sealed class PreviewShape {
  data class SegmentPreview(
    val start: Vec2,
    val end: Vec2,
  ) : PreviewShape()

  data class CirclePreview(
    val center: Vec2,
    val radiusMm: Double,
  ) : PreviewShape()

  data class ArcPreview(
    val center: Vec2,
    val radiusMm: Double,
    val startAngleDeg: Double,
    val sweepAngleDeg: Double,
  ) : PreviewShape()

  data class PolygonPreview(
    val points: List<Vec2>,
    val closed: Boolean,
  ) : PreviewShape()

  data class BoxSelectPreview(
    val cornerA: Vec2,
    val cornerB: Vec2,
  ) : PreviewShape()
}

data class SnapIndicator(
  val position: Vec2,
  val kind: SnapKind,
)

data class DragMoveVertexPreview(
  val vertexOffsets: Map<String, Vec2>,
)

data class SnapSettingsState(
  val snapToGrid: Boolean = true,
  val snapToVertex: Boolean = true,
  val snapToEndpoint: Boolean = true,
  val snapToIntersection: Boolean = true,
  val snapToMidpoint: Boolean = true,
  val snapToPerpendicular: Boolean = true,
  val snapToCenter: Boolean = true,
  val snapToNearestEdge: Boolean = false,
) {
  fun toSnapConfig(
    gridSpacingMm: Double,
    snapRadiusMm: Double,
    geometrySettings: GeometrySettings,
  ): SnapConfig =
    SnapConfig(
      snapToGrid = snapToGrid,
      snapToVertex = snapToVertex,
      snapToEndpoint = snapToEndpoint,
      snapToIntersection = snapToIntersection,
      snapToMidpoint = snapToMidpoint,
      snapToPerpendicular = snapToPerpendicular,
      snapToCenter = snapToCenter,
      snapToNearestEdge = snapToNearestEdge,
      angleSnapEnabled = geometrySettings.angleSnapEnabled,
      angleSnapStepDeg = geometrySettings.angleSnapStepDeg,
      gridSpacingMm = gridSpacingMm,
      snapRadiusMm = snapRadiusMm,
    )
}

data class PendingDialogRequest(
  val kind: DialogKind,
  val anchorPoint: Vec2? = null,
)

enum class DialogKind {
  CIRCLE_RADIUS,
  ROTATE_ANGLE,
  SCALE_FACTOR,
  MOVE_DELTA,
  SYMMETRY_PICKER,
}

data class EditorUiState(
  val isLoading: Boolean = true,
  val loadError: String? = null,
  val document: CreasePatternDocument? = null,
  val activeTool: EditorTool = EditorTool.LINE_SEGMENT,
  val activeAssignment: FoldAssignment = FoldAssignment.MOUNTAIN,
  val multiSelectEnabled: Boolean = false,
  val regularPolygonSides: Int = 6,
  val selection: SelectionState = SelectionState(),
  val viewport: EditorViewportState = EditorViewportState(),
  val showGrid: Boolean = true,
  val snapSettings: SnapSettingsState = SnapSettingsState(),
  val geometrySettings: GeometrySettings = GeometrySettings(),
  val showCoordinates: Boolean = true,
  val isToolPanelExpanded: Boolean = true,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val isDirty: Boolean = false,
  val isSaving: Boolean = false,
  val lastSavedAt: Long? = null,
  val cursorPaperPos: Vec2? = null,
  val snapIndicator: SnapIndicator? = null,
  val pickedPoints: List<Vec2> = emptyList(),
  val pickedVertexIds: List<String> = emptyList(),
  val referenceEdgeId: String? = null,
  val referenceCircleId: String? = null,
  val previewShape: PreviewShape? = null,
  val dragMovePreview: DragMoveVertexPreview? = null,
  val cachedIntersections: List<Vec2> = emptyList(),
  val pendingDialog: PendingDialogRequest? = null,
  val statusBannerMessage: String? = null,
) {
  val instructionText: String
    get() =
      when (activeTool) {
        EditorTool.SELECT ->
          if (multiSelectEnabled) "Multi-Select: Tap items or drag box to select"
          else "Select: Tap vertex/edge/circle, drag vertex or drag box"
        EditorTool.PAN -> "Pan: Drag with 1 finger, pinch with 2 fingers to zoom"
        EditorTool.POINT -> "Point: Tap canvas to place a snapped vertex"
        EditorTool.ERASER -> "Erase: Tap an edge, vertex, circle, or arc to remove"
        EditorTool.LINE_SEGMENT ->
          if (pickedPoints.isEmpty()) "Segment: Drag or tap start point"
          else "Segment: Tap end point"
        EditorTool.RAY ->
          if (pickedPoints.isEmpty()) "Ray: Drag or tap ray origin"
          else "Ray: Tap direction point"
        EditorTool.INFINITE_LINE ->
          if (pickedPoints.isEmpty()) "Infinite Line: Drag or tap first point"
          else "Infinite Line: Tap second point"
        EditorTool.PARALLEL_LINE ->
          if (referenceEdgeId == null) "Parallel: Tap a reference edge"
          else "Parallel: Tap point for parallel line"
        EditorTool.PERPENDICULAR_LINE ->
          if (referenceEdgeId == null) "Perpendicular: Tap a reference edge"
          else "Perpendicular: Tap point for perpendicular line"
        EditorTool.ANGLE_BISECTOR ->
          when (pickedPoints.size) {
            0 -> "Angle Bisector: Tap angle vertex (1/3)"
            1 -> "Angle Bisector: Tap first leg point (2/3)"
            else -> "Angle Bisector: Tap second leg point (3/3)"
          }
        EditorTool.MIDPOINT ->
          if (pickedPoints.isEmpty()) "Midpoint: Tap an edge or first of 2 points"
          else "Midpoint: Tap second point"
        EditorTool.CIRCLE_CENTER_RADIUS ->
          "Circle (Center+R): Drag center → radius, or tap center to enter radius"
        EditorTool.CIRCLE_TWO_POINTS ->
          if (pickedPoints.isEmpty()) "2-Pt Circle: Drag or tap diameter start"
          else "2-Pt Circle: Tap diameter end"
        EditorTool.CIRCLE_THREE_POINTS ->
          "3-Pt Circle: Tap point ${pickedPoints.size + 1} of 3"
        EditorTool.CIRCLE_INTERSECTIONS ->
          if (referenceCircleId == null) "Circle Intersections: Tap a circle"
          else "Circle Intersections: Tap second circle or edge"
        EditorTool.ARC ->
          when (pickedPoints.size) {
            0 -> "Arc: Tap center point (1/3)"
            1 -> "Arc: Tap start point on radius (2/3)"
            else -> "Arc: Tap end angle point (3/3)"
          }
        EditorTool.TRIANGLE -> "Triangle: Tap corner ${pickedPoints.size + 1} of 3"
        EditorTool.RECTANGLE ->
          if (pickedPoints.isEmpty()) "Rectangle: Drag or tap first corner"
          else "Rectangle: Tap opposite corner"
        EditorTool.REGULAR_POLYGON ->
          if (pickedPoints.isEmpty()) "Regular $regularPolygonSides-gon: Drag or tap center"
          else "Regular $regularPolygonSides-gon: Tap vertex radius"
        EditorTool.CUSTOM_POLYGON ->
          if (pickedPoints.isEmpty()) "Polygon: Tap vertices, then tap Finish"
          else "Polygon: ${pickedPoints.size} pts — tap next or Finish"
        EditorTool.POLYGON_FROM_VERTICES ->
          if (pickedVertexIds.isEmpty()) "From Vertices: Tap existing vertices in order"
          else "From Vertices: ${pickedVertexIds.size} selected — tap next or Finish"
        EditorTool.MOVE ->
          if (selection.hasSelection) "Move: Drag selection or tap Move Coords"
          else "Move: Select geometry first (or drag any vertex)"
        EditorTool.ROTATE -> "Rotate: Tap rotation center (or use selection centroid)"
        EditorTool.SCALE -> "Scale: Tap scale center (or use selection centroid)"
        EditorTool.MIRROR_LINE -> "Mirror Axis: Tap any edge to mirror selection across it"
        EditorTool.SYMMETRY -> "Symmetry: Choose symmetry plane or custom 2-pt axis"
        EditorTool.SPLIT_EDGE -> "Split Edge: Tap an edge to split at snapped point"
        EditorTool.EXTEND_LINE -> "Extend: Tap near the endpoint of an edge to extend"
        EditorTool.TRIM_LINE -> "Trim: Tap the edge segment to trim back to intersections"
        EditorTool.JOIN_EDGES ->
          if (referenceEdgeId == null) "Join: Tap first edge"
          else "Join: Tap collinear adjacent edge with same assignment"
      }
}
