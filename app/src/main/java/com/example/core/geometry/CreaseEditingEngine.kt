package com.example.core.geometry

import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object CreaseEditingEngine {

  fun splitEdgeAtPoint(
    doc: CreasePatternDocument,
    edgeId: String,
    splitPoint: Vec2,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return doc
    val vA = doc.vertexById(edge.startVertexId) ?: return doc
    val vB = doc.vertexById(edge.endVertexId) ?: return doc

    val projected = GeometryEngine.projectPointOntoSegment(splitPoint, vA.position, vB.position)
    if (GeometryEngine.distance(projected, vA.position) <= toleranceMm ||
      GeometryEngine.distance(projected, vB.position) <= toleranceMm
    ) {
      return doc
    }

    val vertices = doc.vertices.toMutableList()
    val splitVertex =
      GeometryEngine.findDuplicateVertex(vertices, projected, toleranceMm)
        ?: Vertex(GeometryEngine.newId(), projected).also { vertices.add(it) }

    if (splitVertex.id == vA.id || splitVertex.id == vB.id) return doc

    val edges = doc.edges.filterNot { it.id == edge.id }.toMutableList()
    if (GeometryEngine.findDuplicateEdge(edges, vA.id, splitVertex.id) == null) {
      edges.add(Edge(GeometryEngine.newId(), vA.id, splitVertex.id, edge.assignment))
    }
    if (GeometryEngine.findDuplicateEdge(edges, splitVertex.id, vB.id) == null) {
      edges.add(Edge(GeometryEngine.newId(), splitVertex.id, vB.id, edge.assignment))
    }
    return doc.copy(vertices = vertices, edges = edges, updatedAt = System.currentTimeMillis())
  }

  /**
   * Splits all crossing edges at their mutual intersections (and circle/arc intersections),
   * automatically creating shared intersection vertices without duplicates.
   */
  fun splitAllEdgesAtIntersections(
    doc: CreasePatternDocument,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    val vertices = doc.vertices.toMutableList()
    val vMap = HashMap<String, Vertex>(vertices.size * 2)
    vertices.forEach { vMap[it.id] = it }

    // Collect interior split points for each edge
    val splitPointsPerEdge = HashMap<String, MutableList<Vec2>>()
    for (i in doc.edges.indices) {
      val e1 = doc.edges[i]
      val a1 = vMap[e1.startVertexId]?.position ?: continue
      val b1 = vMap[e1.endVertexId]?.position ?: continue
      val s1 = Segment2D(a1, b1)

      for (j in i + 1 until doc.edges.size) {
        val e2 = doc.edges[j]
        val a2 = vMap[e2.startVertexId]?.position ?: continue
        val b2 = vMap[e2.endVertexId]?.position ?: continue
        val res = IntersectionEngine.intersectSegmentSegment(s1, Segment2D(a2, b2), toleranceMm)
        if (res is SegmentIntersectionResult.Point) {
          val pt = res.point
          if (GeometryEngine.distance(pt, a1) > toleranceMm && GeometryEngine.distance(pt, b1) > toleranceMm) {
            splitPointsPerEdge.getOrPut(e1.id) { mutableListOf() }.add(pt)
          }
          if (GeometryEngine.distance(pt, a2) > toleranceMm && GeometryEngine.distance(pt, b2) > toleranceMm) {
            splitPointsPerEdge.getOrPut(e2.id) { mutableListOf() }.add(pt)
          }
        }
      }

      for (circle in doc.circles) {
        val pts = IntersectionEngine.intersectSegmentCircle(s1, Circle2D(circle.center, circle.radiusMm), toleranceMm)
        for (pt in pts) {
          if (GeometryEngine.distance(pt, a1) > toleranceMm && GeometryEngine.distance(pt, b1) > toleranceMm) {
            splitPointsPerEdge.getOrPut(e1.id) { mutableListOf() }.add(pt)
          }
        }
      }

      for (arc in doc.arcs) {
        val pts = IntersectionEngine.intersectSegmentArc(s1, arc, toleranceMm)
        for (pt in pts) {
          if (GeometryEngine.distance(pt, a1) > toleranceMm && GeometryEngine.distance(pt, b1) > toleranceMm) {
            splitPointsPerEdge.getOrPut(e1.id) { mutableListOf() }.add(pt)
          }
        }
      }
    }

    if (splitPointsPerEdge.isEmpty()) return doc

    val newEdges = mutableListOf<Edge>()
    for (edge in doc.edges) {
      val splits = splitPointsPerEdge[edge.id]
      if (splits.isNullOrEmpty()) {
        newEdges.add(edge)
        continue
      }
      val a = vMap[edge.startVertexId]?.position ?: continue
      val b = vMap[edge.endVertexId]?.position ?: continue
      val dir = GeometryEngine.normalize(b - a) ?: continue

      val uniqueSorted =
        splits
          .sortedBy { GeometryEngine.dot(it - a, dir) }
          .fold(mutableListOf<Vec2>()) { acc, pt ->
            if (acc.none { GeometryEngine.distance(it, pt) <= toleranceMm }) acc.add(pt)
            acc
          }

      val chainVertexIds = mutableListOf<String>()
      chainVertexIds.add(edge.startVertexId)
      for (pt in uniqueSorted) {
        val existing = GeometryEngine.findDuplicateVertex(vertices, pt, toleranceMm)
        val v = existing ?: Vertex(GeometryEngine.newId(), pt).also {
          vertices.add(it)
          vMap[it.id] = it
        }
        if (chainVertexIds.last() != v.id) {
          chainVertexIds.add(v.id)
        }
      }
      if (chainVertexIds.last() != edge.endVertexId) {
        chainVertexIds.add(edge.endVertexId)
      }

      for (k in 0 until chainVertexIds.size - 1) {
        val uId = chainVertexIds[k]
        val wId = chainVertexIds[k + 1]
        if (uId != wId && GeometryEngine.findDuplicateEdge(newEdges, uId, wId) == null) {
          newEdges.add(Edge(GeometryEngine.newId(), uId, wId, edge.assignment))
        }
      }
    }

    return doc.copy(vertices = vertices, edges = newEdges, updatedAt = System.currentTimeMillis())
  }

  /**
   * Joins two collinear edges sharing a degree-2 vertex with identical FoldAssignment.
   */
  fun joinCompatibleEdges(
    doc: CreasePatternDocument,
    edgeId1: String,
    edgeId2: String,
    toleranceMm: Double = 0.05,
  ): CreasePatternDocument {
    if (edgeId1 == edgeId2) return doc
    val e1 = doc.edges.firstOrNull { it.id == edgeId1 } ?: return doc
    val e2 = doc.edges.firstOrNull { it.id == edgeId2 } ?: return doc
    if (e1.assignment != e2.assignment) return doc

    val sharedId =
      when {
        e1.startVertexId == e2.startVertexId || e1.startVertexId == e2.endVertexId -> e1.startVertexId
        e1.endVertexId == e2.startVertexId || e1.endVertexId == e2.endVertexId -> e1.endVertexId
        else -> return doc
      }

    // Ensure shared vertex has degree 2 so we don't break T-junctions
    val incidentCount = doc.edges.count { it.startVertexId == sharedId || it.endVertexId == sharedId }
    if (incidentCount != 2) return doc

    val other1 = if (e1.startVertexId == sharedId) e1.endVertexId else e1.startVertexId
    val other2 = if (e2.startVertexId == sharedId) e2.endVertexId else e2.startVertexId
    if (other1 == other2) return doc

    val p1 = doc.vertexById(other1)?.position ?: return doc
    val pm = doc.vertexById(sharedId)?.position ?: return doc
    val p2 = doc.vertexById(other2)?.position ?: return doc

    val d1 = GeometryEngine.normalize(pm - p1) ?: return doc
    val d2 = GeometryEngine.normalize(p2 - pm) ?: return doc
    // Must point in same direction (collinear continuation, not folded back on itself)
    if (GeometryEngine.dot(d1, d2) < 1.0 - 1e-3) return doc
    if (abs(GeometryEngine.cross(d1, d2)) > toleranceMm) return doc

    val merged = Edge(GeometryEngine.newId(), other1, other2, e1.assignment)
    val newEdges = doc.edges.filterNot { it.id == e1.id || it.id == e2.id } + merged
    val newVertices = doc.vertices.filterNot { it.id == sharedId }
    return doc.copy(vertices = newVertices, edges = newEdges, updatedAt = System.currentTimeMillis())
  }

  /**
   * Extends [edgeId] from its [tapPoint]-nearest endpoint to the nearest intersecting
   * edge, circle, arc, or paper boundary.
   */
  fun extendEdgeTowardTap(
    doc: CreasePatternDocument,
    edgeId: String,
    tapPoint: Vec2,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return doc
    val pA = doc.vertexById(edge.startVertexId)?.position ?: return doc
    val pB = doc.vertexById(edge.endVertexId)?.position ?: return doc

    val extendFromEnd = GeometryEngine.distance(tapPoint, pB) <= GeometryEngine.distance(tapPoint, pA)
    val origin = if (extendFromEnd) pB else pA
    val dir =
      GeometryEngine.normalize(if (extendFromEnd) (pB - pA) else (pA - pB)) ?: return doc
    val ray = Ray2D(origin, dir)

    var bestPoint: Vec2? = null
    var bestDist = Double.POSITIVE_INFINITY

    fun consider(pt: Vec2) {
      val d = GeometryEngine.distance(origin, pt)
      if (d > toleranceMm && d < bestDist) {
        bestDist = d
        bestPoint = pt
      }
    }

    val vMap = doc.vertices.associateBy { it.id }
    for (other in doc.edges) {
      if (other.id == edge.id) continue
      val a = vMap[other.startVertexId]?.position ?: continue
      val b = vMap[other.endVertexId]?.position ?: continue
      IntersectionEngine.intersectRaySegment(ray, Segment2D(a, b), toleranceMm)?.let(::consider)
    }

    for (circle in doc.circles) {
      IntersectionEngine.intersectRayCircle(ray, Circle2D(circle.center, circle.radiusMm), toleranceMm)
        .forEach(::consider)
    }

    // Fallback/limit: paper boundary
    val clippedPaper = IntersectionEngine.clipRayToPaper(ray, doc.paper)
    if (clippedPaper != null) {
      consider(clippedPaper.a)
      consider(clippedPaper.b)
    }

    val target = bestPoint ?: return doc
    val vertices = doc.vertices.toMutableList()
    val targetVertex =
      GeometryEngine.findDuplicateVertex(vertices, target, toleranceMm)
        ?: Vertex(GeometryEngine.newId(), target).also { vertices.add(it) }

    val updatedEdge =
      if (extendFromEnd) edge.copy(endVertexId = targetVertex.id)
      else edge.copy(startVertexId = targetVertex.id)

    val newEdges =
      doc.edges.map { if (it.id == edge.id) updatedEdge else it }
    return doc.copy(vertices = vertices, edges = newEdges, updatedAt = System.currentTimeMillis())
  }

  /**
   * Trims the segment piece of [edgeId] nearest to [tapPoint] back to its neighboring intersections.
   */
  fun trimEdgeAtTap(
    doc: CreasePatternDocument,
    edgeId: String,
    tapPoint: Vec2,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return doc
    val pA = doc.vertexById(edge.startVertexId)?.position ?: return doc
    val pB = doc.vertexById(edge.endVertexId)?.position ?: return doc
    val ab = pB - pA
    val len = GeometryEngine.length(ab)
    if (len <= toleranceMm) return doc
    val unit = ab / len

    val vMap = doc.vertices.associateBy { it.id }
    val splitTs = mutableListOf(0.0, len)

    fun addSplitPt(pt: Vec2) {
      val t = GeometryEngine.dot(pt - pA, unit)
      if (t > toleranceMm && t < len - toleranceMm) {
        if (splitTs.none { abs(it - t) <= toleranceMm }) splitTs.add(t)
      }
    }

    val seg = Segment2D(pA, pB)
    for (other in doc.edges) {
      if (other.id == edge.id) continue
      val oA = vMap[other.startVertexId]?.position ?: continue
      val oB = vMap[other.endVertexId]?.position ?: continue
      val res = IntersectionEngine.intersectSegmentSegment(seg, Segment2D(oA, oB), toleranceMm)
      if (res is SegmentIntersectionResult.Point) {
        addSplitPt(res.point)
      }
    }
    for (circle in doc.circles) {
      IntersectionEngine.intersectSegmentCircle(seg, Circle2D(circle.center, circle.radiusMm), toleranceMm)
        .forEach(::addSplitPt)
    }

    splitTs.sort()
    if (splitTs.size <= 2) {
      // No interior intersections: delete this edge cleanly
      return doc.copy(
        edges = doc.edges.filterNot { it.id == edge.id },
        updatedAt = System.currentTimeMillis(),
      )
    }

    val tapProj = GeometryEngine.projectPointOntoSegment(tapPoint, pA, pB)
    val tapT = GeometryEngine.dot(tapProj - pA, unit).coerceIn(0.0, len)

    var intervalToDrop = 0
    for (k in 0 until splitTs.size - 1) {
      if (tapT >= splitTs[k] - toleranceMm && tapT <= splitTs[k + 1] + toleranceMm) {
        intervalToDrop = k
        break
      }
    }

    val vertices = doc.vertices.toMutableList()
    val remainingEdges = doc.edges.filterNot { it.id == edge.id }.toMutableList()

    fun vertexAtT(t: Double): String {
      if (abs(t) <= toleranceMm) return edge.startVertexId
      if (abs(t - len) <= toleranceMm) return edge.endVertexId
      val pt = pA + unit * t
      val existing = GeometryEngine.findDuplicateVertex(vertices, pt, toleranceMm)
      if (existing != null) return existing.id
      val created = Vertex(GeometryEngine.newId(), pt)
      vertices.add(created)
      return created.id
    }

    for (k in 0 until splitTs.size - 1) {
      if (k == intervalToDrop) continue
      val uId = vertexAtT(splitTs[k])
      val wId = vertexAtT(splitTs[k + 1])
      if (uId != wId && GeometryEngine.findDuplicateEdge(remainingEdges, uId, wId) == null) {
        remainingEdges.add(Edge(GeometryEngine.newId(), uId, wId, edge.assignment))
      }
    }

    return doc.copy(vertices = vertices, edges = remainingEdges, updatedAt = System.currentTimeMillis())
  }

  /**
   * Clusters and merges vertices closer than [toleranceMm], remapping edges and removing
   * zero-length or duplicate edges.
   */
  fun mergeNearbyVertices(
    doc: CreasePatternDocument,
    toleranceMm: Double = 0.5,
  ): CreasePatternDocument {
    val n = doc.vertices.size
    if (n <= 1) return doc
    val visited = BooleanArray(n)
    val remap = HashMap<String, String>(n * 2)
    val mergedVertices = mutableListOf<Vertex>()

    for (i in 0 until n) {
      if (visited[i]) continue
      visited[i] = true
      val cluster = mutableListOf(doc.vertices[i])
      var idx = 0
      while (idx < cluster.size) {
        val cur = cluster[idx++]
        for (j in 0 until n) {
          if (!visited[j] && GeometryEngine.distance(cur.position, doc.vertices[j].position) <= toleranceMm) {
            visited[j] = true
            cluster.add(doc.vertices[j])
          }
        }
      }
      val locked = cluster.firstOrNull { it.isLocked }
      val repPos =
        locked?.position
          ?: Vec2(
            cluster.sumOf { it.position.x } / cluster.size,
            cluster.sumOf { it.position.y } / cluster.size,
          )
      val repVertex = cluster.first().copy(position = repPos, isLocked = locked != null)
      mergedVertices.add(repVertex)
      for (v in cluster) {
        remap[v.id] = repVertex.id
      }
    }

    val dedupedEdges = mutableListOf<Edge>()
    for (edge in doc.edges) {
      val s = remap[edge.startVertexId] ?: continue
      val e = remap[edge.endVertexId] ?: continue
      if (s == e) continue
      if (GeometryEngine.findDuplicateEdge(dedupedEdges, s, e) == null) {
        dedupedEdges.add(edge.copy(startVertexId = s, endVertexId = e))
      }
    }

    return doc.copy(
      vertices = mergedVertices,
      edges = dedupedEdges,
      updatedAt = System.currentTimeMillis(),
    )
  }

  fun setEdgeLength(
    doc: CreasePatternDocument,
    edgeId: String,
    newLengthMm: Double,
  ): CreasePatternDocument {
    if (newLengthMm <= 1e-3) return doc
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return doc
    val start = doc.vertexById(edge.startVertexId) ?: return doc
    val end = doc.vertexById(edge.endVertexId) ?: return doc
    if (end.isLocked) return doc
    val dir = GeometryEngine.normalize(end.position - start.position) ?: return doc
    val newPos = start.position + dir * newLengthMm
    return doc.copy(
      vertices = doc.vertices.map { if (it.id == end.id) it.copy(position = newPos) else it },
      updatedAt = System.currentTimeMillis(),
    )
  }

  fun setEdgeAngle(
    doc: CreasePatternDocument,
    edgeId: String,
    newAngleDeg: Double,
  ): CreasePatternDocument {
    val edge = doc.edges.firstOrNull { it.id == edgeId } ?: return doc
    val start = doc.vertexById(edge.startVertexId) ?: return doc
    val end = doc.vertexById(edge.endVertexId) ?: return doc
    if (end.isLocked) return doc
    val len = GeometryEngine.distance(start.position, end.position)
    if (len <= 1e-4) return doc
    val rad = Math.toRadians(newAngleDeg)
    val newPos = Vec2(start.position.x + len * cos(rad), start.position.y + len * sin(rad))
    return doc.copy(
      vertices = doc.vertices.map { if (it.id == end.id) it.copy(position = newPos) else it },
      updatedAt = System.currentTimeMillis(),
    )
  }
}
