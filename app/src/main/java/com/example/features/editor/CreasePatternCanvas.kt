package com.example.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.geometry.GeometryEngine
import com.example.core.geometry.ViewportTransform
import com.example.core.model.CreasePatternDocument
import com.example.core.model.FoldAssignment
import com.example.core.model.Vec2
import com.example.core.snapping.SnapKind
import com.example.core.snapping.SnapResult
import com.example.ui.theme.DraftingCanvasColors
import com.example.ui.theme.LocalDraftingColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

@Composable
fun CreasePatternCanvas(
  document: CreasePatternDocument,
  state: EditorUiState,
  onTransformViewport: (zoomDelta: Float, panDeltaX: Float, panDeltaY: Float) -> Unit,
  onSnapQuery: (rawPaper: Vec2, pixelsPerMm: Float) -> SnapResult,
  onHoverSnap: (rawPaper: Vec2?, snapResult: SnapResult?) -> Unit,
  onClearTransientPreviews: () -> Unit,
  onPlacePoint: (SnapResult) -> Unit,
  onBeginOrUpdateLineDrag: (startSnap: SnapResult, endSnap: SnapResult) -> Unit,
  onHandleLineTap: (SnapResult) -> Unit,
  onCommitLineSegment: (startPos: Vec2, startVertexId: String?, endPos: Vec2, endVertexId: String?) -> Unit,
  onSelectAt: (paperPoint: Vec2, pixelsPerMm: Float) -> Unit,
  onFindHitVertexForDrag: (paperPoint: Vec2, pixelsPerMm: Float) -> com.example.core.model.Vertex?,
  onUpdateDragVertex: (vertex: com.example.core.model.Vertex, currentPos: Vec2, snapResult: SnapResult) -> Unit,
  onCommitDragVertex: (vertexId: String, originalPos: Vec2, finalPos: Vec2) -> Unit,
  onEraseAt: (paperPoint: Vec2, pixelsPerMm: Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val canvasColors = LocalDraftingColors.current
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }

  val currentDoc by rememberUpdatedState(document)
  val currentState by rememberUpdatedState(state)
  val currentTransform by rememberUpdatedState(
    ViewportTransform(
      canvasWidthPx = canvasSize.width.toFloat(),
      canvasHeightPx = canvasSize.height.toFloat(),
      zoom = state.viewport.zoom,
      panOffsetX = state.viewport.panOffsetX,
      panOffsetY = state.viewport.panOffsetY,
      paper = document.paper,
    )
  )

  val valleyDash = remember { PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f) }
  val constructionDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
  val previewDash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(canvasColors.backdrop)
        .onSizeChanged { canvasSize = it }
        .testTag("crease_pattern_canvas")
        .pointerInput(Unit) {
          val touchSlop = viewConfiguration.touchSlop
          awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val downScreenPos = firstDown.position
            var isMultiTouch = false
            var isDraggingSingle = false
            var totalDragDist = 0f

            val startTransform = currentTransform
            val startRawPaper = startTransform.screenToPaper(downScreenPos.x, downScreenPos.y)
            val startSnap = onSnapQuery(startRawPaper, startTransform.pixelsPerMm)

            val toolAtStart = currentState.activeTool
            val grabbedVertex =
              if (toolAtStart == EditorTool.SELECT) {
                onFindHitVertexForDrag(startRawPaper, startTransform.pixelsPerMm)
              } else {
                null
              }

            if (toolAtStart == EditorTool.POINT || toolAtStart == EditorTool.LINE) {
              onHoverSnap(startRawPaper, startSnap)
            }

            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val pressedChanges = event.changes.filter { it.pressed }

              if (pressedChanges.size >= 2) {
                if (!isMultiTouch) {
                  isMultiTouch = true
                  onClearTransientPreviews()
                }
                val zoomDelta = event.calculateZoom()
                val panDelta = event.calculatePan()
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val centroid = event.calculateCentroid(useCurrent = false)

                if (zoomDelta != 1f || panDelta != Offset.Zero) {
                  val cx = currentTransform.canvasWidthPx / 2f
                  val cy = currentTransform.canvasHeightPx / 2f
                  val currentPanX = currentState.viewport.panOffsetX
                  val currentPanY = currentState.viewport.panOffsetY

                  val zoomAdjustedPanX =
                    if (centroidSize > 0f && zoomDelta != 1f && centroid != Offset.Unspecified) {
                      (centroid.x - (cx + currentPanX)) * (1f - zoomDelta)
                    } else 0f
                  val zoomAdjustedPanY =
                    if (centroidSize > 0f && zoomDelta != 1f && centroid != Offset.Unspecified) {
                      (centroid.y - (cy + currentPanY)) * (1f - zoomDelta)
                    } else 0f

                  onTransformViewport(
                    zoomDelta,
                    panDelta.x + zoomAdjustedPanX,
                    panDelta.y + zoomAdjustedPanY,
                  )
                }
                event.changes.forEach { it.consume() }
              } else if (pressedChanges.size == 1 && !isMultiTouch) {
                val change = pressedChanges.first()
                val delta = change.positionChange()
                totalDragDist += hypot(delta.x, delta.y)
                if (totalDragDist > touchSlop) {
                  isDraggingSingle = true
                }

                val activeTransform = currentTransform
                val curRawPaper = activeTransform.screenToPaper(change.position.x, change.position.y)
                val curSnap = onSnapQuery(curRawPaper, activeTransform.pixelsPerMm)

                when (toolAtStart) {
                  EditorTool.PAN -> {
                    if (delta != Offset.Zero) {
                      onTransformViewport(1f, delta.x, delta.y)
                    }
                  }
                  EditorTool.POINT -> {
                    onHoverSnap(curRawPaper, curSnap)
                  }
                  EditorTool.LINE -> {
                    if (isDraggingSingle) {
                      onBeginOrUpdateLineDrag(startSnap, curSnap)
                    } else {
                      onHoverSnap(curRawPaper, curSnap)
                    }
                  }
                  EditorTool.SELECT -> {
                    if (isDraggingSingle && grabbedVertex != null) {
                      onUpdateDragVertex(grabbedVertex, curRawPaper, curSnap)
                    }
                  }
                  EditorTool.ERASER -> Unit
                }
                change.consume()
              } else if (pressedChanges.isEmpty()) {
                if (!isMultiTouch) {
                  val lastChange = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                  val releasePos = lastChange?.position ?: downScreenPos
                  val endTransform = currentTransform
                  val endRawPaper = endTransform.screenToPaper(releasePos.x, releasePos.y)
                  val endSnap = onSnapQuery(endRawPaper, endTransform.pixelsPerMm)

                  when (toolAtStart) {
                    EditorTool.PAN -> Unit
                    EditorTool.POINT -> {
                      onPlacePoint(endSnap)
                      onClearTransientPreviews()
                    }
                    EditorTool.LINE -> {
                      if (isDraggingSingle) {
                        onCommitLineSegment(
                          startSnap.position,
                          startSnap.snappedVertexId,
                          endSnap.position,
                          endSnap.snappedVertexId,
                        )
                      } else {
                        onHandleLineTap(endSnap)
                      }
                    }
                    EditorTool.SELECT -> {
                      if (isDraggingSingle && grabbedVertex != null) {
                        onCommitDragVertex(
                          grabbedVertex.id,
                          grabbedVertex.position,
                          endSnap.position,
                        )
                      } else {
                        onSelectAt(endRawPaper, endTransform.pixelsPerMm)
                      }
                    }
                    EditorTool.ERASER -> {
                      onEraseAt(endRawPaper, endTransform.pixelsPerMm)
                    }
                  }
                } else {
                  onClearTransientPreviews()
                }
                break
              }
            }
          }
        }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val doc = currentDoc
      val uiState = currentState
      val transform =
        ViewportTransform(
          canvasWidthPx = size.width,
          canvasHeightPx = size.height,
          zoom = uiState.viewport.zoom,
          panOffsetX = uiState.viewport.panOffsetX,
          panOffsetY = uiState.viewport.panOffsetY,
          paper = doc.paper,
        )

      val (paperLeft, paperTop) = transform.paperToScreen(Vec2(0.0, 0.0))
      val (paperRight, paperBottom) =
        transform.paperToScreen(Vec2(doc.paper.widthMm, doc.paper.heightMm))
      val paperW = paperRight - paperLeft
      val paperH = paperBottom - paperTop

      // 1. Paper drop shadow & sheet background
      val shadowOffset = 4.dp.toPx()
      drawRect(
        color = canvasColors.paperShadow,
        topLeft = Offset(paperLeft + shadowOffset, paperTop + shadowOffset),
        size = Size(paperW, paperH),
      )
      drawRect(
        color = canvasColors.paperSheet,
        topLeft = Offset(paperLeft, paperTop),
        size = Size(paperW, paperH),
      )

      // 2. Configurable Grid inside paper bounds
      if (uiState.showGrid && doc.paper.gridSpacingMm > GeometryEngine.EPSILON) {
        drawPaperGrid(
          transform = transform,
          paperLeft = paperLeft,
          paperTop = paperTop,
          paperRight = paperRight,
          paperBottom = paperBottom,
          colors = canvasColors,
        )
      }

      // 3. Build effective vertex map (applying live drag preview if any)
      val dragPreview = uiState.dragVertexPreview
      val vertexPosMap =
        doc.vertices.associate { v ->
          val pos =
            if (dragPreview != null && dragPreview.vertexId == v.id) {
              dragPreview.currentPosition
            } else {
              v.position
            }
          v.id to pos
        }

      // 4. Draw edges (construction/unassigned first, then valley/mountain, then boundary)
      val orderedEdges =
        doc.edges.sortedBy { edge ->
          when (edge.assignment) {
            FoldAssignment.CONSTRUCTION -> 0
            FoldAssignment.UNASSIGNED -> 1
            FoldAssignment.VALLEY -> 2
            FoldAssignment.MOUNTAIN -> 3
            FoldAssignment.BOUNDARY -> 4
          }
        }

      for (edge in orderedEdges) {
        val p1 = vertexPosMap[edge.startVertexId] ?: continue
        val p2 = vertexPosMap[edge.endVertexId] ?: continue
        val (x1, y1) = transform.paperToScreen(p1)
        val (x2, y2) = transform.paperToScreen(p2)
        val startOffset = Offset(x1, y1)
        val endOffset = Offset(x2, y2)

        val isSelected = uiState.selection.selectedEdgeId == edge.id
        if (isSelected) {
          drawLine(
            color = canvasColors.selection.copy(alpha = 0.45f),
            start = startOffset,
            end = endOffset,
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }

        val strokeWidth =
          when (edge.assignment) {
            FoldAssignment.BOUNDARY -> 2.4.dp.toPx()
            FoldAssignment.MOUNTAIN, FoldAssignment.VALLEY -> 2.1.dp.toPx()
            FoldAssignment.UNASSIGNED -> 1.7.dp.toPx()
            FoldAssignment.CONSTRUCTION -> 1.3.dp.toPx()
          }
        val pathEffect =
          when (edge.assignment) {
            FoldAssignment.VALLEY -> valleyDash
            FoldAssignment.CONSTRUCTION -> constructionDash
            else -> null
          }
        drawLine(
          color = canvasColors.colorFor(edge.assignment),
          start = startOffset,
          end = endOffset,
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round,
          pathEffect = pathEffect,
        )
      }

      // 5. Pending line rubber-band preview
      val pending = uiState.pendingLinePreview
      if (pending != null) {
        val (sx, sy) = transform.paperToScreen(pending.startPosition)
        val startPx = Offset(sx, sy)
        val endPaper = pending.currentEndPosition
        if (endPaper != null) {
          val (ex, ey) = transform.paperToScreen(endPaper)
          drawLine(
            color = canvasColors.colorFor(uiState.activeAssignment).copy(alpha = 0.85f),
            start = startPx,
            end = Offset(ex, ey),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = previewDash,
          )
        }
        drawCircle(
          color = canvasColors.selection,
          radius = 6.dp.toPx(),
          center = startPx,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      // 6. Vertices
      for (vertex in doc.vertices) {
        val pos = vertexPosMap[vertex.id] ?: continue
        val (vx, vy) = transform.paperToScreen(pos)
        val center = Offset(vx, vy)
        val isSelected = uiState.selection.selectedVertexId == vertex.id
        if (isSelected) {
          drawCircle(
            color = canvasColors.selection.copy(alpha = 0.35f),
            radius = 9.dp.toPx(),
            center = center,
          )
          drawCircle(
            color = canvasColors.selection,
            radius = 5.dp.toPx(),
            center = center,
          )
        } else {
          drawCircle(
            color = canvasColors.paperSheet,
            radius = 3.2.dp.toPx(),
            center = center,
          )
          drawCircle(
            color = canvasColors.vertexDot,
            radius = 2.2.dp.toPx(),
            center = center,
          )
        }
      }

      // 7. Snap Reticle Indicator
      val snap = uiState.snapIndicator
      if (snap != null && snap.kind != SnapKind.NONE) {
        val (sx, sy) = transform.paperToScreen(snap.position)
        val center = Offset(sx, sy)
        if (snap.kind == SnapKind.VERTEX) {
          val r = 8.dp.toPx()
          val diamond =
            Path().apply {
              moveTo(center.x, center.y - r)
              lineTo(center.x + r, center.y)
              lineTo(center.x, center.y + r)
              lineTo(center.x - r, center.y)
              close()
            }
          drawPath(
            path = diamond,
            color = canvasColors.snapVertex,
            style = Stroke(width = 2.dp.toPx()),
          )
        } else if (snap.kind == SnapKind.GRID) {
          val arm = 7.dp.toPx()
          drawLine(
            color = canvasColors.snapGrid,
            start = Offset(center.x - arm, center.y),
            end = Offset(center.x + arm, center.y),
            strokeWidth = 1.8.dp.toPx(),
          )
          drawLine(
            color = canvasColors.snapGrid,
            start = Offset(center.x, center.y - arm),
            end = Offset(center.x, center.y + arm),
            strokeWidth = 1.8.dp.toPx(),
          )
        }
      }
    }
  }
}

private fun DrawScope.drawPaperGrid(
  transform: ViewportTransform,
  paperLeft: Float,
  paperTop: Float,
  paperRight: Float,
  paperBottom: Float,
  colors: DraftingCanvasColors,
) {
  var stepMm = transform.paper.gridSpacingMm
  val minPxSpacing = 12.dp.toPx()
  while (stepMm * transform.pixelsPerMm < minPxSpacing && stepMm < transform.paper.widthMm) {
    stepMm *= 2.0
  }

  val xCount = floor(transform.paper.widthMm / stepMm).toInt()
  val yCount = floor(transform.paper.heightMm / stepMm).toInt()
  val minorStroke = 0.8.dp.toPx()
  val majorStroke = 1.2.dp.toPx()

  for (i in 1..xCount) {
    val xMm = i * stepMm
    if (xMm >= transform.paper.widthMm - 1e-4) continue
    val (sx, _) = transform.paperToScreen(Vec2(xMm, 0.0))
    val isMajor = i % 4 == 0
    drawLine(
      color = if (isMajor) colors.gridMajor else colors.gridMinor,
      start = Offset(sx, paperTop),
      end = Offset(sx, paperBottom),
      strokeWidth = if (isMajor) majorStroke else minorStroke,
    )
  }

  for (j in 1..yCount) {
    val yMm = j * stepMm
    if (yMm >= transform.paper.heightMm - 1e-4) continue
    val (_, sy) = transform.paperToScreen(Vec2(0.0, yMm))
    val isMajor = j % 4 == 0
    drawLine(
      color = if (isMajor) colors.gridMajor else colors.gridMinor,
      start = Offset(paperLeft, sy),
      end = Offset(paperRight, sy),
      strokeWidth = if (isMajor) majorStroke else minorStroke,
    )
  }
  ceil(0.0) // keep math import clean if needed
}
