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
import androidx.compose.ui.graphics.Color
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
import com.example.core.model.Vertex
import com.example.core.snapping.SnapKind
import com.example.core.snapping.SnapResult
import com.example.ui.theme.DraftingCanvasColors
import com.example.ui.theme.LocalDraftingColors
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun CreasePatternCanvas(
  document: CreasePatternDocument,
  state: EditorUiState,
  onTransformViewport: (zoomDelta: Float, panDeltaX: Float, panDeltaY: Float) -> Unit,
  onSnapQuery: (rawPaper: Vec2, pixelsPerMm: Float, anchorPoint: Vec2?) -> SnapResult,
  onHoverSnap: (rawPaper: Vec2?, snapResult: SnapResult?) -> Unit,
  onClearTransientPreviews: () -> Unit,
  onFindHitVertexForDrag: (paperPoint: Vec2, pixelsPerMm: Float) -> Vertex?,
  onSingleFingerDrag: (
    startRaw: Vec2,
    startSnap: SnapResult,
    curRaw: Vec2,
    curSnap: SnapResult,
    grabbedVertex: Vertex?,
    moveSelectionActive: Boolean,
  ) -> Unit,
  onSingleFingerDragRelease: (
    startRaw: Vec2,
    startSnap: SnapResult,
    endRaw: Vec2,
    endSnap: SnapResult,
    grabbedVertex: Vertex?,
  ) -> Unit,
  onCanvasTap: (rawPaper: Vec2, snapResult: SnapResult, pixelsPerMm: Float) -> Unit,
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
            val startSnap = onSnapQuery(startRawPaper, startTransform.pixelsPerMm, null)
            val toolAtStart = currentState.activeTool
            val grabbedVertex =
              if (toolAtStart == EditorTool.SELECT || toolAtStart == EditorTool.MOVE) {
                onFindHitVertexForDrag(startRawPaper, startTransform.pixelsPerMm)
              } else {
                null
              }
            val moveSelectionActive =
              toolAtStart == EditorTool.MOVE && currentState.selection.hasSelection

            onHoverSnap(startRawPaper, startSnap)

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
                val anchor = if (isDraggingSingle) startSnap.position else null
                val curSnap = onSnapQuery(curRawPaper, activeTransform.pixelsPerMm, anchor)

                if (toolAtStart == EditorTool.PAN) {
                  if (delta != Offset.Zero) {
                    onTransformViewport(1f, delta.x, delta.y)
                  }
                } else if (isDraggingSingle) {
                  onSingleFingerDrag(
                    startRawPaper,
                    startSnap,
                    curRawPaper,
                    curSnap,
                    grabbedVertex,
                    moveSelectionActive,
                  )
                } else {
                  onHoverSnap(curRawPaper, curSnap)
                }
                change.consume()
              } else if (pressedChanges.isEmpty()) {
                if (!isMultiTouch) {
                  val lastChange = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                  val releasePos = lastChange?.position ?: downScreenPos
                  val endTransform = currentTransform
                  val endRawPaper = endTransform.screenToPaper(releasePos.x, releasePos.y)
                  val anchor = if (isDraggingSingle) startSnap.position else null
                  val endSnap = onSnapQuery(endRawPaper, endTransform.pixelsPerMm, anchor)

                  if (toolAtStart != EditorTool.PAN) {
                    if (isDraggingSingle) {
                      onSingleFingerDragRelease(
                        startRawPaper,
                        startSnap,
                        endRawPaper,
                        endSnap,
                        grabbedVertex,
                      )
                    } else {
                      onCanvasTap(endRawPaper, endSnap, endTransform.pixelsPerMm)
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

      // 2. Configurable Grid
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

      // 3. Build effective vertex position map (applying live drag offsets)
      val dragOffsets = uiState.dragMovePreview?.vertexOffsets
      val vertexPosMap =
        doc.vertices.associate { v ->
          val pos = dragOffsets?.get(v.id) ?: v.position
          v.id to pos
        }

      // 4. Draw Circles & Arcs
      val showConstruction = uiState.geometrySettings.showConstructionLines
      for (circle in doc.circles) {
        if (!showConstruction && circle.assignment == FoldAssignment.CONSTRUCTION) continue
        val (cx, cy) = transform.paperToScreen(circle.center)
        val radiusPx = (circle.radiusMm * transform.pixelsPerMm).toFloat()
        val isSelected = circle.id in uiState.selection.selectedCircleIds || circle.id == uiState.referenceCircleId
        if (isSelected) {
          drawCircle(
            color = canvasColors.selection.copy(alpha = 0.45f),
            radius = radiusPx,
            center = Offset(cx, cy),
            style = Stroke(width = 7.dp.toPx()),
          )
        }
        val pathEffect =
          when (circle.assignment) {
            FoldAssignment.VALLEY -> valleyDash
            FoldAssignment.CONSTRUCTION -> constructionDash
            else -> null
          }
        drawCircle(
          color = canvasColors.colorFor(circle.assignment),
          radius = radiusPx,
          center = Offset(cx, cy),
          style = Stroke(width = 1.8.dp.toPx(), pathEffect = pathEffect),
        )
        // Center dot
        drawCircle(
          color = canvasColors.colorFor(circle.assignment).copy(alpha = 0.7f),
          radius = 2.5.dp.toPx(),
          center = Offset(cx, cy),
        )
      }

      for (arc in doc.arcs) {
        if (!showConstruction && arc.assignment == FoldAssignment.CONSTRUCTION) continue
        val (cx, cy) = transform.paperToScreen(arc.center)
        val radiusPx = (arc.radiusMm * transform.pixelsPerMm).toFloat()
        val isSelected = arc.id in uiState.selection.selectedArcIds
        val topLeft = Offset(cx - radiusPx, cy - radiusPx)
        val arcSize = Size(radiusPx * 2f, radiusPx * 2f)
        if (isSelected) {
          drawArc(
            color = canvasColors.selection.copy(alpha = 0.45f),
            startAngle = arc.startAngleDeg.toFloat(),
            sweepAngle = arc.sweepAngleDeg.toFloat(),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
          )
        }
        val pathEffect =
          when (arc.assignment) {
            FoldAssignment.VALLEY -> valleyDash
            FoldAssignment.CONSTRUCTION -> constructionDash
            else -> null
          }
        drawArc(
          color = canvasColors.colorFor(arc.assignment),
          startAngle = arc.startAngleDeg.toFloat(),
          sweepAngle = arc.sweepAngleDeg.toFloat(),
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, pathEffect = pathEffect),
        )
      }

      // 5. Draw Edges
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
        if (!showConstruction && edge.assignment == FoldAssignment.CONSTRUCTION) continue
        val p1 = vertexPosMap[edge.startVertexId] ?: continue
        val p2 = vertexPosMap[edge.endVertexId] ?: continue
        val (x1, y1) = transform.paperToScreen(p1)
        val (x2, y2) = transform.paperToScreen(p2)
        val startOffset = Offset(x1, y1)
        val endOffset = Offset(x2, y2)

        val isSelected = edge.id in uiState.selection.selectedEdgeIds || edge.id == uiState.referenceEdgeId
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

      // 6. Computed Intersection Markers
      if (uiState.geometrySettings.showIntersections) {
        val markerR = 3.5.dp.toPx()
        for (hit in uiState.cachedIntersections) {
          val (ix, iy) = transform.paperToScreen(hit)
          drawCircle(
            color = Color(0xFFEA580C).copy(alpha = 0.85f),
            radius = markerR,
            center = Offset(ix, iy),
            style = Stroke(width = 1.4.dp.toPx()),
          )
        }
      }

      // 7. Live Preview Shape (drag / multi-step tool)
      val activeColor = canvasColors.colorFor(uiState.activeAssignment).copy(alpha = 0.85f)
      when (val preview = uiState.previewShape) {
        is PreviewShape.SegmentPreview -> {
          val (sx, sy) = transform.paperToScreen(preview.start)
          val (ex, ey) = transform.paperToScreen(preview.end)
          drawLine(
            color = activeColor,
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 2.3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = previewDash,
          )
        }
        is PreviewShape.CirclePreview -> {
          val (cx, cy) = transform.paperToScreen(preview.center)
          val rPx = (preview.radiusMm * transform.pixelsPerMm).toFloat()
          drawCircle(
            color = activeColor,
            radius = rPx,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx(), pathEffect = previewDash),
          )
        }
        is PreviewShape.ArcPreview -> {
          val (cx, cy) = transform.paperToScreen(preview.center)
          val rPx = (preview.radiusMm * transform.pixelsPerMm).toFloat()
          drawArc(
            color = activeColor,
            startAngle = preview.startAngleDeg.toFloat(),
            sweepAngle = preview.sweepAngleDeg.toFloat(),
            useCenter = false,
            topLeft = Offset(cx - rPx, cy - rPx),
            size = Size(rPx * 2f, rPx * 2f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = previewDash),
          )
        }
        is PreviewShape.PolygonPreview -> {
          val pts = preview.points
          for (i in 0 until pts.size - 1) {
            val (x1, y1) = transform.paperToScreen(pts[i])
            val (x2, y2) = transform.paperToScreen(pts[i + 1])
            drawLine(
              color = activeColor,
              start = Offset(x1, y1),
              end = Offset(x2, y2),
              strokeWidth = 2.1.dp.toPx(),
              pathEffect = previewDash,
            )
          }
          if (preview.closed && pts.size >= 3) {
            val (x1, y1) = transform.paperToScreen(pts.last())
            val (x2, y2) = transform.paperToScreen(pts.first())
            drawLine(
              color = activeColor,
              start = Offset(x1, y1),
              end = Offset(x2, y2),
              strokeWidth = 2.1.dp.toPx(),
              pathEffect = previewDash,
            )
          }
        }
        is PreviewShape.BoxSelectPreview -> {
          val (x1, y1) = transform.paperToScreen(preview.cornerA)
          val (x2, y2) = transform.paperToScreen(preview.cornerB)
          val left = min(x1, x2)
          val top = min(y1, y2)
          val w = max(1f, kotlin.math.abs(x2 - x1))
          val h = max(1f, kotlin.math.abs(y2 - y1))
          drawRect(
            color = canvasColors.selection.copy(alpha = 0.14f),
            topLeft = Offset(left, top),
            size = Size(w, h),
          )
          drawRect(
            color = canvasColors.selection,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = previewDash),
          )
        }
        null -> Unit
      }

      // 8. Picked multi-step anchor points
      for (pt in uiState.pickedPoints) {
        val (px, py) = transform.paperToScreen(pt)
        drawCircle(
          color = canvasColors.selection,
          radius = 6.dp.toPx(),
          center = Offset(px, py),
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      // 9. Vertices
      for (vertex in doc.vertices) {
        val pos = vertexPosMap[vertex.id] ?: continue
        val (vx, vy) = transform.paperToScreen(pos)
        val center = Offset(vx, vy)
        val isSelected = vertex.id in uiState.selection.selectedVertexIds || vertex.id in uiState.pickedVertexIds
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
            radius = 3.0.dp.toPx(),
            center = center,
          )
          drawCircle(
            color = canvasColors.vertexDot,
            radius = 2.0.dp.toPx(),
            center = center,
          )
        }
      }

      // 10. Distinct Snap Reticle Indicators
      val snap = uiState.snapIndicator
      if (snap != null && snap.kind != SnapKind.NONE) {
        drawSnapIndicator(
          transform = transform,
          snap = snap,
          colors = canvasColors,
        )
      }
    }
  }
}

private fun DrawScope.drawSnapIndicator(
  transform: ViewportTransform,
  snap: SnapIndicator,
  colors: DraftingCanvasColors,
) {
  val (sx, sy) = transform.paperToScreen(snap.position)
  val center = Offset(sx, sy)
  val r = 8.dp.toPx()

  when (snap.kind) {
    SnapKind.VERTEX -> {
      val diamond =
        Path().apply {
          moveTo(center.x, center.y - r)
          lineTo(center.x + r, center.y)
          lineTo(center.x, center.y + r)
          lineTo(center.x - r, center.y)
          close()
        }
      drawPath(diamond, color = colors.snapVertex, style = Stroke(width = 2.dp.toPx()))
    }
    SnapKind.ENDPOINT -> {
      drawRect(
        color = colors.snapVertex,
        topLeft = Offset(center.x - r * 0.8f, center.y - r * 0.8f),
        size = Size(r * 1.6f, r * 1.6f),
        style = Stroke(width = 2.dp.toPx()),
      )
    }
    SnapKind.INTERSECTION -> {
      val c = Color(0xFFEA580C)
      drawLine(c, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), strokeWidth = 2.2.dp.toPx())
      drawLine(c, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), strokeWidth = 2.2.dp.toPx())
    }
    SnapKind.MIDPOINT -> {
      val c = Color(0xFF0D9488)
      val tri =
        Path().apply {
          moveTo(center.x, center.y - r)
          lineTo(center.x + r * 0.86f, center.y + r * 0.65f)
          lineTo(center.x - r * 0.86f, center.y + r * 0.65f)
          close()
        }
      drawPath(tri, color = c, style = Stroke(width = 2.dp.toPx()))
    }
    SnapKind.CENTER -> {
      val c = Color(0xFF8B5CF6)
      drawCircle(color = c, radius = r, center = center, style = Stroke(width = 2.dp.toPx()))
      drawCircle(color = c, radius = 2.5.dp.toPx(), center = center)
    }
    SnapKind.PERPENDICULAR -> {
      val c = colors.valley
      val path =
        Path().apply {
          moveTo(center.x - r * 0.8f, center.y + r * 0.8f)
          lineTo(center.x - r * 0.8f, center.y - r * 0.2f)
          lineTo(center.x + r * 0.2f, center.y - r * 0.2f)
          lineTo(center.x + r * 0.2f, center.y + r * 0.8f)
        }
      drawPath(path, color = c, style = Stroke(width = 2.dp.toPx()))
    }
    SnapKind.NEAREST_EDGE -> {
      drawCircle(color = colors.selection, radius = r * 0.7f, center = center, style = Stroke(width = 2.dp.toPx()))
    }
    SnapKind.GRID -> {
      val arm = 7.dp.toPx()
      drawLine(colors.snapGrid, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 1.8.dp.toPx())
      drawLine(colors.snapGrid, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 1.8.dp.toPx())
    }
    SnapKind.NONE -> Unit
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
}
