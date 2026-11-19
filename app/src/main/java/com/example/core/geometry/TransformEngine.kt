package com.example.core.geometry

import com.example.core.model.ArcPrimitive
import com.example.core.model.CirclePrimitive
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

sealed class SymmetrySpec {
  data object HorizontalCenter : SymmetrySpec()

  data object VerticalCenter : SymmetrySpec()

  data object DiagonalMain : SymmetrySpec()

  data object DiagonalAnti : SymmetrySpec()

  data class CustomAxis(val a: Vec2, val b: Vec2) : SymmetrySpec()

  data class Rotational(val center: Vec2, val count: Int) : SymmetrySpec()
}

object TransformEngine {

  fun translate(point: Vec2, delta: Vec2): Vec2 = point + delta

  fun rotate(point: Vec2, center: Vec2, angleDeg: Double): Vec2 {
    val rad = Math.toRadians(angleDeg)
    val c = cos(rad)
    val s = sin(rad)
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Vec2(
      x = center.x + dx * c - dy * s,
      y = center.y + dx * s + dy * c,
    )
  }

  fun scale(point: Vec2, center: Vec2, factor: Double): Vec2 {
    return Vec2(
      x = center.x + (point.x - center.x) * factor,
      y = center.y + (point.y - center.y) * factor,
    )
  }

  fun reflectAcrossLine(point: Vec2, lineA: Vec2, lineB: Vec2): Vec2 {
    val dir = GeometryEngine.normalize(lineB - lineA) ?: return point
    val ap = point - lineA
    val proj = lineA + dir * GeometryEngine.dot(ap, dir)
    return proj * 2.0 - point
  }

  fun flipHorizontal(point: Vec2, axisX: Double): Vec2 = Vec2(2.0 * axisX - point.x, point.y)

  fun flipVertical(point: Vec2, axisY: Double): Vec2 = Vec2(point.x, 2.0 * axisY - point.y)

  fun addSegmentWithDeduplication(
    doc: CreasePatternDocument,
    startPos: Vec2,
    endPos: Vec2,
    assignment: FoldAssignment,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    if (GeometryEngine.distance(startPos, endPos) <= toleranceMm) return doc
    val vertices = doc.vertices.toMutableList()
    val edges = doc.edges.toMutableList()

    val vStart =
      GeometryEngine.findDuplicateVertex(vertices, startPos, toleranceMm)
        ?: Vertex(GeometryEngine.newId(), startPos).also { vertices.add(it) }
    val vEnd =
      GeometryEngine.findDuplicateVertex(vertices, endPos, toleranceMm)
        ?: Vertex(GeometryEngine.newId(), endPos).also { vertices.add(it) }

    if (vStart.id == vEnd.id) return doc
    val existingEdge = GeometryEngine.findDuplicateEdge(edges, vStart.id, vEnd.id)
    if (existingEdge == null) {
      edges.add(Edge(GeometryEngine.newId(), vStart.id, vEnd.id, assignment))
    } else if (existingEdge.assignment != assignment) {
      val idx = edges.indexOfFirst { it.id == existingEdge.id }
      if (idx >= 0) edges[idx] = existingEdge.copy(assignment = assignment)
    }
    return doc.copy(vertices = vertices, edges = edges, updatedAt = System.currentTimeMillis())
  }

  fun addPolyline(
    doc: CreasePatternDocument,
    points: List<Vec2>,
    assignment: FoldAssignment,
    closed: Boolean,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    if (points.size < 2) return doc
    var current = doc
    for (i in 0 until points.size - 1) {
      current = addSegmentWithDeduplication(current, points[i], points[i + 1], assignment, toleranceMm)
    }
    if (closed && points.size >= 3) {
      current = addSegmentWithDeduplication(current, points.last(), points.first(), assignment, toleranceMm)
    }
    return current
  }

  fun transformSelection(
    doc: CreasePatternDocument,
    selectedVertexIds: Set<String>,
    selectedEdgeIds: Set<String>,
    selectedCircleIds: Set<String> = emptySet(),
    selectedArcIds: Set<String> = emptySet(),
    duplicate: Boolean,
    toleranceMm: Double = 0.01,
    scaleFactorForCircles: Double = 1.0,
    rotationDeltaDegForArcs: Double = 0.0,
    isReflectionForArcs: Boolean = false,
    transformPoint: (Vec2) -> Vec2,
  ): CreasePatternDocument {
    val affectedEdges = doc.edges.filter { it.id in selectedEdgeIds }
    val allVertexIds =
      buildSet {
        addAll(selectedVertexIds)
        affectedEdges.forEach {
          add(it.startVertexId)
          add(it.endVertexId)
        }
      }

    if (!duplicate) {
      val updatedVertices =
        doc.vertices.map { v ->
          if (v.id in allVertexIds && !v.isLocked) v.copy(position = transformPoint(v.position))
          else v
        }
      val updatedCircles =
        doc.circles.map { c ->
          if (c.id in selectedCircleIds) {
            c.copy(
              center = transformPoint(c.center),
              radiusMm = abs(c.radiusMm * scaleFactorForCircles).coerceAtLeast(0.1),
            )
          } else c
        }
      val updatedArcs =
        doc.arcs.map { a ->
          if (a.id in selectedArcIds) {
            transformArc(a, transformPoint, scaleFactorForCircles, rotationDeltaDegForArcs, isReflectionForArcs)
          } else a
        }
      return doc.copy(
        vertices = updatedVertices,
        circles = updatedCircles,
        arcs = updatedArcs,
        updatedAt = System.currentTimeMillis(),
      )
    }

    // Duplicate mode: clone selected vertices, edges, circles, and arcs with deduplication
    val newVertices = doc.vertices.toMutableList()
    val newEdges = doc.edges.toMutableList()
    val newCircles = doc.circles.toMutableList()
    val newArcs = doc.arcs.toMutableList()

    val vertexRemap = HashMap<String, String>()
    for (v in doc.vertices) {
      if (v.id !in allVertexIds) continue
      val mappedPos = transformPoint(v.position)
      val existing = GeometryEngine.findDuplicateVertex(newVertices, mappedPos, toleranceMm)
      if (existing != null) {
        vertexRemap[v.id] = existing.id
      } else {
        val cloned = Vertex(id = GeometryEngine.newId(), position = mappedPos)
        newVertices.add(cloned)
        vertexRemap[v.id] = cloned.id
      }
    }

    // Clone explicitly selected edges, plus any edge whose both endpoints are in selectedVertexIds
    val edgesToClone =
      doc.edges.filter {
        it.id in selectedEdgeIds ||
          (it.startVertexId in selectedVertexIds && it.endVertexId in selectedVertexIds)
      }
    for (edge in edgesToClone) {
      val newStart = vertexRemap[edge.startVertexId] ?: continue
      val newEnd = vertexRemap[edge.endVertexId] ?: continue
      if (newStart == newEnd) continue
      if (GeometryEngine.findDuplicateEdge(newEdges, newStart, newEnd) == null) {
        newEdges.add(Edge(GeometryEngine.newId(), newStart, newEnd, edge.assignment))
      }
    }

    for (circle in doc.circles) {
      if (circle.id !in selectedCircleIds) continue
      val newCenter = transformPoint(circle.center)
      val newRadius = abs(circle.radiusMm * scaleFactorForCircles).coerceAtLeast(0.1)
      val dup =
        newCircles.any {
          GeometryEngine.distance(it.center, newCenter) <= toleranceMm &&
            abs(it.radiusMm - newRadius) <= toleranceMm
        }
      if (!dup) {
        newCircles.add(circle.copy(id = GeometryEngine.newId(), center = newCenter, radiusMm = newRadius))
      }
    }

    for (arc in doc.arcs) {
      if (arc.id !in selectedArcIds) continue
      val transformed =
        transformArc(arc, transformPoint, scaleFactorForCircles, rotationDeltaDegForArcs, isReflectionForArcs)
      newArcs.add(transformed.copy(id = GeometryEngine.newId()))
    }

    return doc.copy(
      vertices = newVertices,
      edges = newEdges,
      circles = newCircles,
      arcs = newArcs,
      updatedAt = System.currentTimeMillis(),
    )
  }

  fun applySymmetry(
    doc: CreasePatternDocument,
    selectedVertexIds: Set<String>,
    selectedEdgeIds: Set<String>,
    selectedCircleIds: Set<String> = emptySet(),
    selectedArcIds: Set<String> = emptySet(),
    symmetry: SymmetrySpec,
    toleranceMm: Double = 0.01,
  ): CreasePatternDocument {
    // If nothing explicitly selected, mirror all non-boundary geometry so the tool is immediately useful
    val useAll =
      selectedVertexIds.isEmpty() &&
        selectedEdgeIds.isEmpty() &&
        selectedCircleIds.isEmpty() &&
        selectedArcIds.isEmpty()

    val effectiveEdges =
      if (useAll) {
        doc.edges.filter { it.assignment != com.example.core.model.FoldAssignment.BOUNDARY }.map { it.id }.toSet()
      } else {
        selectedEdgeIds
      }
    val effectiveVertices =
      if (useAll && effectiveEdges.isEmpty()) doc.vertices.map { it.id }.toSet() else selectedVertexIds
    val effectiveCircles = if (useAll) doc.circles.map { it.id }.toSet() else selectedCircleIds
    val effectiveArcs = if (useAll) doc.arcs.map { it.id }.toSet() else selectedArcIds

    return when (symmetry) {
      SymmetrySpec.HorizontalCenter -> {
        val axisY = doc.paper.heightMm * 0.5
        transformSelection(
          doc = doc,
          selectedVertexIds = effectiveVertices,
          selectedEdgeIds = effectiveEdges,
          selectedCircleIds = effectiveCircles,
          selectedArcIds = effectiveArcs,
          duplicate = true,
          toleranceMm = toleranceMm,
          isReflectionForArcs = true,
        ) { pt -> flipVertical(pt, axisY) }
      }
      SymmetrySpec.VerticalCenter -> {
        val axisX = doc.paper.widthMm * 0.5
        transformSelection(
          doc = doc,
          selectedVertexIds = effectiveVertices,
          selectedEdgeIds = effectiveEdges,
          selectedCircleIds = effectiveCircles,
          selectedArcIds = effectiveArcs,
          duplicate = true,
          toleranceMm = toleranceMm,
          isReflectionForArcs = true,
        ) { pt -> flipHorizontal(pt, axisX) }
      }
      SymmetrySpec.DiagonalMain -> {
        val a = Vec2(0.0, 0.0)
        val b = Vec2(doc.paper.widthMm, doc.paper.heightMm)
        transformSelection(
          doc = doc,
          selectedVertexIds = effectiveVertices,
          selectedEdgeIds = effectiveEdges,
          selectedCircleIds = effectiveCircles,
          selectedArcIds = effectiveArcs,
          duplicate = true,
          toleranceMm = toleranceMm,
          isReflectionForArcs = true,
        ) { pt -> reflectAcrossLine(pt, a, b) }
      }
      SymmetrySpec.DiagonalAnti -> {
        val a = Vec2(doc.paper.widthMm, 0.0)
        val b = Vec2(0.0, doc.paper.heightMm)
        transformSelection(
          doc = doc,
          selectedVertexIds = effectiveVertices,
          selectedEdgeIds = effectiveEdges,
          selectedCircleIds = effectiveCircles,
          selectedArcIds = effectiveArcs,
          duplicate = true,
          toleranceMm = toleranceMm,
          isReflectionForArcs = true,
        ) { pt -> reflectAcrossLine(pt, a, b) }
      }
      is SymmetrySpec.CustomAxis -> {
        transformSelection(
          doc = doc,
          selectedVertexIds = effectiveVertices,
          selectedEdgeIds = effectiveEdges,
          selectedCircleIds = effectiveCircles,
          selectedArcIds = effectiveArcs,
          duplicate = true,
          toleranceMm = toleranceMm,
          isReflectionForArcs = true,
        ) { pt -> reflectAcrossLine(pt, symmetry.a, symmetry.b) }
      }
      is SymmetrySpec.Rotational -> {
        val count = symmetry.count.coerceIn(2, 24)
        val stepDeg = 360.0 / count
        var current = doc
        for (k in 1 until count) {
          val deg = stepDeg * k
          current =
            transformSelection(
              doc = current,
              selectedVertexIds = effectiveVertices,
              selectedEdgeIds = effectiveEdges,
              selectedCircleIds = effectiveCircles,
              selectedArcIds = effectiveArcs,
              duplicate = true,
              toleranceMm = toleranceMm,
              rotationDeltaDegForArcs = deg,
            ) { pt -> rotate(pt, symmetry.center, deg) }
        }
        current
      }
    }
  }

  fun computeSelectionCentroid(
    doc: CreasePatternDocument,
    selectedVertexIds: Set<String>,
    selectedEdgeIds: Set<String>,
    selectedCircleIds: Set<String> = emptySet(),
    selectedArcIds: Set<String> = emptySet(),
  ): Vec2 {
    val pts = mutableListOf<Vec2>()
    val vMap = doc.vertices.associateBy { it.id }
    selectedVertexIds.forEach { vMap[it]?.position?.let(pts::add) }
    selectedEdgeIds.forEach { edgeId ->
      val edge = doc.edges.firstOrNull { it.id == edgeId }
      if (edge != null) {
        vMap[edge.startVertexId]?.position?.let(pts::add)
        vMap[edge.endVertexId]?.position?.let(pts::add)
      }
    }
    doc.circles.filter { it.id in selectedCircleIds }.forEach { pts.add(it.center) }
    doc.arcs.filter { it.id in selectedArcIds }.forEach { pts.add(it.center) }
    if (pts.isEmpty()) return Vec2(doc.paper.widthMm * 0.5, doc.paper.heightMm * 0.5)
    val sumX = pts.sumOf { it.x }
    val sumY = pts.sumOf { it.y }
    return Vec2(sumX / pts.size, sumY / pts.size)
  }

  private fun transformArc(
    arc: ArcPrimitive,
    transformPoint: (Vec2) -> Vec2,
    scaleFactor: Double,
    rotationDeg: Double,
    isReflection: Boolean,
  ): ArcPrimitive {
    val newCenter = transformPoint(arc.center)
    val startRad = Math.toRadians(arc.startAngleDeg)
    val endRad = Math.toRadians(arc.startAngleDeg + arc.sweepAngleDeg)
    val startPt =
      Vec2(
        arc.center.x + arc.radiusMm * cos(startRad),
        arc.center.y + arc.radiusMm * sin(startRad),
      )
    val endPt =
      Vec2(
        arc.center.x + arc.radiusMm * cos(endRad),
        arc.center.y + arc.radiusMm * sin(endRad),
      )
    val tStart = transformPoint(startPt)
    val tEnd = transformPoint(endPt)
    val newRadius = abs(arc.radiusMm * scaleFactor).coerceAtLeast(0.1)
    return if (isReflection) {
      val a1 = GeometryEngine.angleDegrees(newCenter, tEnd)
      val a2 = GeometryEngine.angleDegrees(newCenter, tStart)
      val sweep = GeometryEngine.normalizeAngleDeg(a2 - a1).let { if (it < 1e-3) 360.0 else it }
      arc.copy(center = newCenter, radiusMm = newRadius, startAngleDeg = a1, sweepAngleDeg = sweep)
    } else {
      arc.copy(
        center = newCenter,
        radiusMm = newRadius,
        startAngleDeg = GeometryEngine.normalizeAngleDeg(arc.startAngleDeg + rotationDeg),
      )
    }
  }
}
