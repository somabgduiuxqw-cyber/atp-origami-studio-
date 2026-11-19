package com.example.features.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.data.model.OrigamiTemplateId
import com.example.data.model.TemplateCatalog
import com.example.data.serializers.PreviewEdgeDto
import com.example.data.serializers.PreviewSnapshotDto
import com.example.ui.theme.LocalDraftingColors
import kotlin.math.min

@Composable
fun CreaseThumbnail(
  preview: PreviewSnapshotDto?,
  modifier: Modifier = Modifier,
) {
  val canvasColors = LocalDraftingColors.current
  val shape = RoundedCornerShape(10.dp)

  Box(
    modifier =
      modifier
        .clip(shape)
        .background(canvasColors.backdrop)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
        .padding(6.dp)
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val widthMm = (preview?.widthMm ?: 200f).coerceAtLeast(1f)
      val heightMm = (preview?.heightMm ?: 200f).coerceAtLeast(1f)
      val scale = min(size.width / widthMm, size.height / heightMm) * 0.92f
      val drawW = widthMm * scale
      val drawH = heightMm * scale
      val originX = (size.width - drawW) / 2f
      val originY = (size.height - drawH) / 2f

      drawRect(
        color = canvasColors.paperSheet,
        topLeft = Offset(originX, originY),
        size = Size(drawW, drawH),
      )

      val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
      val edges = preview?.edges.orEmpty()
      for (edge in edges) {
        val assignment =
          runCatching { FoldAssignment.valueOf(edge.assignment) }
            .getOrDefault(FoldAssignment.UNASSIGNED)
        val color = canvasColors.colorFor(assignment)
        val strokeWidth =
          when (assignment) {
            FoldAssignment.BOUNDARY -> 1.8.dp.toPx()
            FoldAssignment.MOUNTAIN, FoldAssignment.VALLEY -> 1.5.dp.toPx()
            else -> 1.1.dp.toPx()
          }
        val pathEffect =
          when (assignment) {
            FoldAssignment.VALLEY, FoldAssignment.CONSTRUCTION -> dashEffect
            else -> null
          }
        drawLine(
          color = color,
          start = Offset(originX + edge.x1 * scale, originY + edge.y1 * scale),
          end = Offset(originX + edge.x2 * scale, originY + edge.y2 * scale),
          strokeWidth = strokeWidth,
          cap = StrokeCap.Round,
          pathEffect = pathEffect,
        )
      }

      for (circle in preview?.circles.orEmpty()) {
        val assignment =
          runCatching { FoldAssignment.valueOf(circle.assignment) }
            .getOrDefault(FoldAssignment.CONSTRUCTION)
        drawCircle(
          color = canvasColors.colorFor(assignment),
          radius = circle.radiusMm * scale,
          center = Offset(originX + circle.cx * scale, originY + circle.cy * scale),
          style =
            androidx.compose.ui.graphics.drawscope.Stroke(
              width = 1.1.dp.toPx(),
              pathEffect = if (assignment == FoldAssignment.CONSTRUCTION) dashEffect else null,
            ),
        )
      }
    }
  }
}

@Composable
fun TemplateThumbnail(
  templateId: OrigamiTemplateId,
  modifier: Modifier = Modifier,
) {
  val snapshot =
    remember(templateId) {
      val doc =
        TemplateCatalog.buildDocument(
          templateId = templateId,
          projectId = "preview_${templateId.name}",
          projectName = templateId.name,
          paper = PaperSpec(200.0, 200.0, 25.0),
          now = 0L,
        )
      val vertexMap = doc.vertices.associateBy { it.id }
      val edges =
        doc.edges.mapNotNull { e ->
          val a = vertexMap[e.startVertexId]?.position ?: return@mapNotNull null
          val b = vertexMap[e.endVertexId]?.position ?: return@mapNotNull null
          PreviewEdgeDto(
            x1 = a.x.toFloat(),
            y1 = a.y.toFloat(),
            x2 = b.x.toFloat(),
            y2 = b.y.toFloat(),
            assignment = e.assignment.name,
          )
        }
      PreviewSnapshotDto(widthMm = 200f, heightMm = 200f, edges = edges)
    }

  CreaseThumbnail(preview = snapshot, modifier = modifier)
}
