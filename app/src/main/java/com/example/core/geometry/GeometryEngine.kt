package com.example.core.geometry

import com.example.core.model.ArcPrimitive
import com.example.core.model.CirclePrimitive
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import com.example.core.model.FoldAssignment
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

object GeometryEngine {
  const val EPSILON: Double = 1e-6
  const val MERGE_TOLERANCE_MM: Double = 1e-3

  fun distance(a: Vec2, b: Vec2): Double = hypot(b.x - a.x, b.y - a.y)

  fun distanceSquared(a: Vec2, b: Vec2): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return dx * dx + dy * dy
  }

  fun dot(a: Vec2, b: Vec2): Double = a.x * b.x + a.y * b.y

  fun cross(a: Vec2, b: Vec2): Double = a.x * b.y - a.y * b.x

  fun length(v: Vec2): Double = hypot(v.x, v.y)

  fun normalize(v: Vec2): Vec2? {
    val len = length(v)
    return if (len <= EPSILON) null else Vec2(v.x / len, v.y / len)
  }

  fun perpendicularUnit(dir: Vec2): Vec2? {
    val unit = normalize(dir) ?: return null
    return Vec2(-unit.y, unit.x)
  }

  fun midpoint(a: Vec2, b: Vec2): Vec2 = Vec2((a.x + b.x) * 0.5, (a.y + b.y) * 0.5)

  fun pointToSegmentDistance(point: Vec2, a: Vec2, b: Vec2): Double {
    val projected = projectPointOntoSegment(point, a, b)
    return distance(point, projected)
  }

  fun projectPointOntoSegment(point: Vec2, a: Vec2, b: Vec2): Vec2 {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq <= EPSILON) return a
    val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lenSq).coerceIn(0.0, 1.0)
    return Vec2(a.x + t * dx, a.y + t * dy)
  }

  fun projectPointOntoInfiniteLine(point: Vec2, lineOrigin: Vec2, lineDir: Vec2): Vec2 {
    val unit = normalize(lineDir) ?: return lineOrigin
    val t = dot(point - lineOrigin, unit)
    return lineOrigin + unit * t
  }

  fun angleDegrees(a: Vec2, b: Vec2): Double {
    val deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
    return normalizeAngleDeg(deg)
  }

  fun normalizeAngleDeg(deg: Double): Double {
    var result = deg % 360.0
    if (result < 0.0) result += 360.0
    return result
  }

  fun isAngleOnArc(angleDeg: Double, startAngleDeg: Double, sweepAngleDeg: Double, tolDeg: Double = 0.5): Boolean {
    if (abs(sweepAngleDeg) >= 360.0 - tolDeg) return true
    val normAngle = normalizeAngleDeg(angleDeg)
    val normStart = normalizeAngleDeg(startAngleDeg)
    return if (sweepAngleDeg >= 0.0) {
      val diff = normalizeAngleDeg(normAngle - normStart)
      diff <= sweepAngleDeg + tolDeg || diff >= 360.0 - tolDeg
    } else {
      val diff = normalizeAngleDeg(normStart - normAngle)
      diff <= abs(sweepAngleDeg) + tolDeg || diff >= 360.0 - tolDeg
    }
  }

  fun snapAngleFromAnchor(anchor: Vec2, rawPoint: Vec2, stepDeg: Double): Vec2 {
    val dist = distance(anchor, rawPoint)
    if (dist <= EPSILON || stepDeg <= EPSILON) return rawPoint
    val rawAngle = angleDegrees(anchor, rawPoint)
    val snappedDeg = round(rawAngle / stepDeg) * stepDeg
    val rad = Math.toRadians(snappedDeg)
    return Vec2(anchor.x + dist * cos(rad), anchor.y + dist * sin(rad))
  }

  fun findNearestVertex(
    document: CreasePatternDocument,
    point: Vec2,
    maxDistanceMm: Double,
  ): Vertex? {
    var bestVertex: Vertex? = null
    var bestDist = maxDistanceMm
    for (vertex in document.vertices) {
      val d = distance(vertex.position, point)
      if (d <= bestDist) {
        bestDist = d
        bestVertex = vertex
      }
    }
    return bestVertex
  }

  fun findNearestEdge(
    document: CreasePatternDocument,
    point: Vec2,
    maxDistanceMm: Double,
  ): Edge? {
    val vertexMap = document.vertices.associateBy { it.id }
    var bestEdge: Edge? = null
    var bestDist = maxDistanceMm
    for (edge in document.edges) {
      val a = vertexMap[edge.startVertexId]?.position ?: continue
      val b = vertexMap[edge.endVertexId]?.position ?: continue
      val d = pointToSegmentDistance(point, a, b)
      if (d <= bestDist) {
        bestDist = d
        bestEdge = edge
      }
    }
    return bestEdge
  }

  fun findNearestCircle(
    document: CreasePatternDocument,
    point: Vec2,
    maxDistanceMm: Double,
  ): CirclePrimitive? {
    var best: CirclePrimitive? = null
    var bestDist = maxDistanceMm
    for (circle in document.circles) {
      val d = abs(distance(circle.center, point) - circle.radiusMm)
      if (d <= bestDist) {
        bestDist = d
        best = circle
      }
    }
    return best
  }

  fun findNearestArc(
    document: CreasePatternDocument,
    point: Vec2,
    maxDistanceMm: Double,
  ): ArcPrimitive? {
    var best: ArcPrimitive? = null
    var bestDist = maxDistanceMm
    for (arc in document.arcs) {
      val ang = angleDegrees(arc.center, point)
      if (!isAngleOnArc(ang, arc.startAngleDeg, arc.sweepAngleDeg, tolDeg = 4.0)) continue
      val d = abs(distance(arc.center, point) - arc.radiusMm)
      if (d <= bestDist) {
        bestDist = d
        best = arc
      }
    }
    return best
  }

  fun findDuplicateVertex(
    vertices: List<Vertex>,
    point: Vec2,
    toleranceMm: Double = MERGE_TOLERANCE_MM,
  ): Vertex? {
    var closest: Vertex? = null
    var best = toleranceMm
    for (v in vertices) {
      val d = distance(v.position, point)
      if (d <= best) {
        best = d
        closest = v
      }
    }
    return closest
  }

  fun findDuplicateEdge(edges: List<Edge>, v1Id: String, v2Id: String): Edge? {
    return edges.firstOrNull {
      (it.startVertexId == v1Id && it.endVertexId == v2Id) ||
        (it.startVertexId == v2Id && it.endVertexId == v1Id)
    }
  }

  fun segmentIntersection(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec2? {
    val result =
      IntersectionEngine.intersectSegmentSegment(
        Segment2D(a1, a2),
        Segment2D(b1, b2),
        tolerance = EPSILON,
      )
    return when (result) {
      is SegmentIntersectionResult.Point -> result.point
      else -> null
    }
  }

  fun clampToPaper(point: Vec2, paper: PaperSpec): Vec2 {
    return Vec2(
      x = min(max(point.x, 0.0), paper.widthMm),
      y = min(max(point.y, 0.0), paper.heightMm),
    )
  }

  fun createBoundaryVerticesAndEdges(
    widthMm: Double,
    heightMm: Double,
  ): Pair<List<Vertex>, List<Edge>> {
    val tl = Vertex(id = newId(), position = Vec2(0.0, 0.0))
    val tr = Vertex(id = newId(), position = Vec2(widthMm, 0.0))
    val br = Vertex(id = newId(), position = Vec2(widthMm, heightMm))
    val bl = Vertex(id = newId(), position = Vec2(0.0, heightMm))
    val edges =
      listOf(
        Edge(id = newId(), startVertexId = tl.id, endVertexId = tr.id, assignment = FoldAssignment.BOUNDARY),
        Edge(id = newId(), startVertexId = tr.id, endVertexId = br.id, assignment = FoldAssignment.BOUNDARY),
        Edge(id = newId(), startVertexId = br.id, endVertexId = bl.id, assignment = FoldAssignment.BOUNDARY),
        Edge(id = newId(), startVertexId = bl.id, endVertexId = tl.id, assignment = FoldAssignment.BOUNDARY),
      )
    return listOf(tl, tr, br, bl) to edges
  }

  fun newId(): String = UUID.randomUUID().toString()
}
