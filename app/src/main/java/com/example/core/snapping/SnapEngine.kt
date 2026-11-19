package com.example.core.snapping

import com.example.core.geometry.GeometryEngine
import com.example.core.geometry.IntersectionEngine
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Vec2
import kotlin.math.round

enum class SnapKind {
  NONE,
  VERTEX,
  ENDPOINT,
  INTERSECTION,
  MIDPOINT,
  CENTER,
  PERPENDICULAR,
  NEAREST_EDGE,
  GRID,
}

data class SnapConfig(
  val snapToGrid: Boolean = true,
  val snapToVertex: Boolean = true,
  val snapToEndpoint: Boolean = true,
  val snapToIntersection: Boolean = true,
  val snapToMidpoint: Boolean = true,
  val snapToPerpendicular: Boolean = true,
  val snapToCenter: Boolean = true,
  val snapToNearestEdge: Boolean = false,
  val angleSnapEnabled: Boolean = false,
  val angleSnapStepDeg: Double = 22.5,
  val gridSpacingMm: Double = 25.0,
  val snapRadiusMm: Double = 8.0,
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
    anchorPoint: Vec2? = null,
    precomputedIntersections: List<Vec2>? = null,
  ): SnapResult {
    val candidateRaw =
      if (config.angleSnapEnabled && anchorPoint != null) {
        GeometryEngine.snapAngleFromAnchor(anchorPoint, rawPaperPoint, config.angleSnapStepDeg)
      } else {
        rawPaperPoint
      }

    val radius = config.snapRadiusMm
    val vertexMap = document.vertices.associateBy { it.id }

    // Priority 1: Existing Vertices
    if (config.snapToVertex) {
      val vertex =
        GeometryEngine.findNearestVertex(
          document = document,
          point = candidateRaw,
          maxDistanceMm = radius,
        )
      if (vertex != null) {
        return SnapResult(
          position = vertex.position,
          kind = SnapKind.VERTEX,
          snappedVertexId = vertex.id,
        )
      }
    }

    // Priority 2: Edge Endpoints (also covers arc endpoints)
    if (config.snapToEndpoint) {
      var bestEndpoint: Vec2? = null
      var bestDist = radius
      var bestVertexId: String? = null
      for (edge in document.edges) {
        val vStart = vertexMap[edge.startVertexId]
        if (vStart != null) {
          val d = GeometryEngine.distance(candidateRaw, vStart.position)
          if (d <= bestDist) {
            bestDist = d
            bestEndpoint = vStart.position
            bestVertexId = vStart.id
          }
        }
        val vEnd = vertexMap[edge.endVertexId]
        if (vEnd != null) {
          val d = GeometryEngine.distance(candidateRaw, vEnd.position)
          if (d <= bestDist) {
            bestDist = d
            bestEndpoint = vEnd.position
            bestVertexId = vEnd.id
          }
        }
      }
      if (bestEndpoint != null) {
        return SnapResult(
          position = bestEndpoint,
          kind = SnapKind.ENDPOINT,
          snappedVertexId = bestVertexId,
        )
      }
    }

    // Priority 3: Computed Geometric Intersections
    if (config.snapToIntersection) {
      val intersections =
        precomputedIntersections ?: IntersectionEngine.computeAllIntersections(document)
      var bestHit: Vec2? = null
      var bestDist = radius
      for (hit in intersections) {
        val d = GeometryEngine.distance(candidateRaw, hit)
        if (d <= bestDist) {
          bestDist = d
          bestHit = hit
        }
      }
      if (bestHit != null) {
        return SnapResult(position = bestHit, kind = SnapKind.INTERSECTION)
      }
    }

    // Priority 4: Edge Midpoints
    if (config.snapToMidpoint) {
      var bestMid: Vec2? = null
      var bestDist = radius * 0.9
      for (edge in document.edges) {
        val a = vertexMap[edge.startVertexId]?.position ?: continue
        val b = vertexMap[edge.endVertexId]?.position ?: continue
        val mid = GeometryEngine.midpoint(a, b)
        val d = GeometryEngine.distance(candidateRaw, mid)
        if (d <= bestDist) {
          bestDist = d
          bestMid = mid
        }
      }
      if (bestMid != null) {
        return SnapResult(position = bestMid, kind = SnapKind.MIDPOINT)
      }
    }

    // Priority 5: Circle/Arc Centers & Paper Center
    if (config.snapToCenter) {
      val centers = buildList {
        add(Vec2(document.paper.widthMm * 0.5, document.paper.heightMm * 0.5))
        document.circles.forEach { add(it.center) }
        document.arcs.forEach { add(it.center) }
      }
      var bestCenter: Vec2? = null
      var bestDist = radius * 0.9
      for (c in centers) {
        val d = GeometryEngine.distance(candidateRaw, c)
        if (d <= bestDist) {
          bestDist = d
          bestCenter = c
        }
      }
      if (bestCenter != null) {
        return SnapResult(position = bestCenter, kind = SnapKind.CENTER)
      }
    }

    // Priority 6: Perpendicular Foot from active anchor point onto edges
    if (config.snapToPerpendicular && anchorPoint != null) {
      var bestPerp: Vec2? = null
      var bestDist = radius * 0.85
      for (edge in document.edges) {
        val a = vertexMap[edge.startVertexId]?.position ?: continue
        val b = vertexMap[edge.endVertexId]?.position ?: continue
        val foot = GeometryEngine.projectPointOntoSegment(anchorPoint, a, b)
        val d = GeometryEngine.distance(candidateRaw, foot)
        if (d <= bestDist && GeometryEngine.distance(anchorPoint, foot) > 1e-3) {
          bestDist = d
          bestPerp = foot
        }
      }
      if (bestPerp != null) {
        return SnapResult(position = bestPerp, kind = SnapKind.PERPENDICULAR)
      }
    }

    // Priority 7: Grid Intersections (placed before generic edge projection so grid corners win cleanly)
    if (config.snapToGrid && config.gridSpacingMm > GeometryEngine.EPSILON) {
      val step = config.gridSpacingMm
      val gx = round(candidateRaw.x / step) * step
      val gy = round(candidateRaw.y / step) * step
      val gridPoint = Vec2(gx, gy)
      if (GeometryEngine.distance(candidateRaw, gridPoint) <= radius * 0.8) {
        return SnapResult(position = gridPoint, kind = SnapKind.GRID)
      }
    }

    // Priority 8: Nearest Point on Any Edge
    if (config.snapToNearestEdge) {
      var bestOnEdge: Vec2? = null
      var bestDist = radius * 0.8
      for (edge in document.edges) {
        val a = vertexMap[edge.startVertexId]?.position ?: continue
        val b = vertexMap[edge.endVertexId]?.position ?: continue
        val proj = GeometryEngine.projectPointOntoSegment(candidateRaw, a, b)
        val d = GeometryEngine.distance(candidateRaw, proj)
        if (d <= bestDist) {
          bestDist = d
          bestOnEdge = proj
        }
      }
      if (bestOnEdge != null) {
        return SnapResult(position = bestOnEdge, kind = SnapKind.NEAREST_EDGE)
      }
    }

    return SnapResult(position = candidateRaw, kind = SnapKind.NONE)
  }
}
