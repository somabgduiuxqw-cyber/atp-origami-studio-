package com.example.core.geometry

import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object GeometryEngine {
  const val EPSILON: Double = 1e-6
  const val MERGE_TOLERANCE_MM: Double = 1e-3

  fun distance(a: Vec2, b: Vec2): Double = hypot(b.x - a.x, b.y - a.y)

  fun distanceSquared(a: Vec2, b: Vec2): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return dx * dx + dy * dy
  }

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

  fun angleDegrees(a: Vec2, b: Vec2): Double {
    val deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
    return if (deg < 0.0) deg + 360.0 else deg
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
    val d1x = a2.x - a1.x
    val d1y = a2.y - a1.y
    val d2x = b2.x - b1.x
    val d2y = b2.y - b1.y
    val denom = d1x * d2y - d1y * d2x
    if (abs(denom) <= EPSILON) return null
    val dx = b1.x - a1.x
    val dy = b1.y - a1.y
    val t = (dx * d2y - dy * d2x) / denom
    val u = (dx * d1y - dy * d1x) / denom
    if (t in -EPSILON..(1.0 + EPSILON) && u in -EPSILON..(1.0 + EPSILON)) {
      return Vec2(a1.x + t.coerceIn(0.0, 1.0) * d1x, a1.y + t.coerceIn(0.0, 1.0) * d1y)
    }
    return null
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
