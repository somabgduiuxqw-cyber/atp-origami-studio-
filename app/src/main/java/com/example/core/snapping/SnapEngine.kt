package com.example.core.snapping

import com.example.core.geometry.GeometryEngine
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Vec2
import kotlin.math.round

enum class SnapKind {
  NONE,
  GRID,
  VERTEX,
}

data class SnapConfig(
  val snapToGrid: Boolean = true,
  val snapToVertex: Boolean = true,
  val gridSpacingMm: Double = 25.0,
  val vertexSnapRadiusMm: Double = 8.0,
  val gridSnapRadiusMm: Double = 6.0,
)

data class SnapResult(
  val position: Vec2,
  val kind: SnapKind,
  val snappedVertexId: String? = null,
)

object SnapEngine {
  fun resolveSnap(
    rawPaperPoint: Vec2,
    document: CreasePatternDocument,
    config: SnapConfig,
  ): SnapResult {
    if (config.snapToVertex) {
      val vertex =
        GeometryEngine.findNearestVertex(
          document = document,
          point = rawPaperPoint,
          maxDistanceMm = config.vertexSnapRadiusMm,
        )
      if (vertex != null) {
        return SnapResult(
          position = vertex.position,
          kind = SnapKind.VERTEX,
          snappedVertexId = vertex.id,
        )
      }
    }

    if (config.snapToGrid && config.gridSpacingMm > GeometryEngine.EPSILON) {
      val step = config.gridSpacingMm
      val gx = round(rawPaperPoint.x / step) * step
      val gy = round(rawPaperPoint.y / step) * step
      val gridPoint = Vec2(gx, gy)
      if (GeometryEngine.distance(rawPaperPoint, gridPoint) <= config.gridSnapRadiusMm) {
        return SnapResult(
          position = gridPoint,
          kind = SnapKind.GRID,
          snappedVertexId = null,
        )
      }
    }

    return SnapResult(position = rawPaperPoint, kind = SnapKind.NONE, snappedVertexId = null)
  }
}
