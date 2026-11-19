package com.example.core.geometry

import com.example.core.model.ArcPrimitive
import com.example.core.model.CirclePrimitive
import com.example.core.model.CreasePatternDocument
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.spatial.SpatialGridIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Line2D(val point: Vec2, val direction: Vec2)

data class Ray2D(val origin: Vec2, val direction: Vec2)

data class Segment2D(val a: Vec2, val b: Vec2)

data class Circle2D(val center: Vec2, val radius: Double)

sealed class LineIntersectionResult {
  data object Parallel : LineIntersectionResult()

  data object Coincident : LineIntersectionResult()

  data class Point(val point: Vec2) : LineIntersectionResult()
}

sealed class SegmentIntersectionResult {
  data object None : SegmentIntersectionResult()

  data class Overlap(val start: Vec2, val end: Vec2) : SegmentIntersectionResult()

  data class Point(val point: Vec2) : SegmentIntersectionResult()
}

sealed class CircleIntersectionResult {
  data object None : CircleIntersectionResult()

  data object Coincident : CircleIntersectionResult()

  data class Tangent(val point: Vec2) : CircleIntersectionResult()

  data class TwoPoints(val first: Vec2, val second: Vec2) : CircleIntersectionResult()
}

object IntersectionEngine {

  fun intersectLineLine(
    l1: Line2D,
    l2: Line2D,
    tolerance: Double = 1e-6,
  ): LineIntersectionResult {
    val d1 = GeometryEngine.normalize(l1.direction) ?: return LineIntersectionResult.Parallel
    val d2 = GeometryEngine.normalize(l2.direction) ?: return LineIntersectionResult.Parallel
    val denom = GeometryEngine.cross(d1, d2)
    val diff = l2.point - l1.point
    if (abs(denom) <= tolerance) {
      val perpDist = abs(GeometryEngine.cross(diff, d1))
      return if (perpDist <= tolerance) {
        LineIntersectionResult.Coincident
      } else {
        LineIntersectionResult.Parallel
      }
    }
    val t = GeometryEngine.cross(diff, d2) / denom
    return LineIntersectionResult.Point(l1.point + d1 * t)
  }

  fun intersectSegmentSegment(
    s1: Segment2D,
    s2: Segment2D,
    tolerance: Double = 1e-5,
  ): SegmentIntersectionResult {
    val r = s1.b - s1.a
    val s = s2.b - s2.a
    val rLen = GeometryEngine.length(r)
    val sLen = GeometryEngine.length(s)
    if (rLen <= tolerance || sLen <= tolerance) return SegmentIntersectionResult.None

    val rUnit = r / rLen
    val sUnit = s / sLen
    val denomUnit = GeometryEngine.cross(rUnit, sUnit)
    val qp = s2.a - s1.a

    if (abs(denomUnit) <= 1e-6) {
      val perpDist = abs(GeometryEngine.cross(qp, rUnit))
      if (perpDist > tolerance) return SegmentIntersectionResult.None

      // Collinear segments: project onto rUnit
      val t0 = GeometryEngine.dot(s2.a - s1.a, rUnit)
      val t1 = GeometryEngine.dot(s2.b - s1.a, rUnit)
      val seg2Min = min(t0, t1)
      val seg2Max = max(t0, t1)
      val overlapStart = max(0.0, seg2Min)
      val overlapEnd = min(rLen, seg2Max)

      if (overlapEnd < overlapStart - tolerance) return SegmentIntersectionResult.None
      if (abs(overlapEnd - overlapStart) <= tolerance) {
        val midT = ((overlapStart + overlapEnd) * 0.5).coerceIn(0.0, rLen)
        return SegmentIntersectionResult.Point(s1.a + rUnit * midT)
      }
      return SegmentIntersectionResult.Overlap(
        start = s1.a + rUnit * overlapStart,
        end = s1.a + rUnit * overlapEnd,
      )
    }

    val denom = GeometryEngine.cross(r, s)
    val t = GeometryEngine.cross(qp, s) / denom
    val u = GeometryEngine.cross(qp, r) / denom
    val tTol = tolerance / rLen
    val uTol = tolerance / sLen
    if (t in -tTol..(1.0 + tTol) && u in -uTol..(1.0 + uTol)) {
      val clampedT = t.coerceIn(0.0, 1.0)
      return SegmentIntersectionResult.Point(s1.a + r * clampedT)
    }
    return SegmentIntersectionResult.None
  }

  fun intersectRayLine(
    ray: Ray2D,
    line: Line2D,
    tolerance: Double = 1e-6,
  ): LineIntersectionResult {
    val d1 = GeometryEngine.normalize(ray.direction) ?: return LineIntersectionResult.Parallel
    val d2 = GeometryEngine.normalize(line.direction) ?: return LineIntersectionResult.Parallel
    val denom = GeometryEngine.cross(d1, d2)
    val diff = line.point - ray.origin
    if (abs(denom) <= tolerance) {
      val perpDist = abs(GeometryEngine.cross(diff, d1))
      return if (perpDist <= tolerance) LineIntersectionResult.Coincident else LineIntersectionResult.Parallel
    }
    val t = GeometryEngine.cross(diff, d2) / denom
    if (t < -tolerance) return LineIntersectionResult.Parallel // behind ray origin
    return LineIntersectionResult.Point(ray.origin + d1 * max(0.0, t))
  }

  fun intersectRaySegment(
    ray: Ray2D,
    seg: Segment2D,
    tolerance: Double = 1e-5,
  ): Vec2? {
    val d1 = GeometryEngine.normalize(ray.direction) ?: return null
    val s = seg.b - seg.a
    val sLen = GeometryEngine.length(s)
    if (sLen <= tolerance) return null
    val denom = GeometryEngine.cross(d1, s)
    if (abs(denom) <= 1e-7) return null
    val qp = seg.a - ray.origin
    val t = GeometryEngine.cross(qp, s) / denom
    val u = GeometryEngine.cross(qp, d1) / denom
    val uTol = tolerance / sLen
    if (t >= -tolerance && u in -uTol..(1.0 + uTol)) {
      return ray.origin + d1 * max(0.0, t)
    }
    return null
  }

  fun intersectLineCircle(
    line: Line2D,
    circle: Circle2D,
    tolerance: Double = 1e-5,
  ): CircleIntersectionResult {
    if (circle.radius <= tolerance) return CircleIntersectionResult.None
    val d = GeometryEngine.normalize(line.direction) ?: return CircleIntersectionResult.None
    val oc = line.point - circle.center
    val b = GeometryEngine.dot(oc, d)
    val c = GeometryEngine.dot(oc, oc) - circle.radius * circle.radius
    val discriminant = b * b - c

    if (discriminant < -tolerance * circle.radius) {
      return CircleIntersectionResult.None
    }
    if (abs(discriminant) <= tolerance * circle.radius) {
      val t = -b
      return CircleIntersectionResult.Tangent(line.point + d * t)
    }
    val root = sqrt(max(0.0, discriminant))
    val p1 = line.point + d * (-b - root)
    val p2 = line.point + d * (-b + root)
    return if (GeometryEngine.distance(p1, p2) <= tolerance) {
      CircleIntersectionResult.Tangent(GeometryEngine.midpoint(p1, p2))
    } else {
      CircleIntersectionResult.TwoPoints(p1, p2)
    }
  }

  fun intersectSegmentCircle(
    seg: Segment2D,
    circle: Circle2D,
    tolerance: Double = 1e-5,
  ): List<Vec2> {
    val diff = seg.b - seg.a
    val segLen = GeometryEngine.length(diff)
    if (segLen <= tolerance) return emptyList()
    val unit = diff / segLen
    val lineResult = intersectLineCircle(Line2D(seg.a, unit), circle, tolerance)
    val candidates =
      when (lineResult) {
        is CircleIntersectionResult.Tangent -> listOf(lineResult.point)
        is CircleIntersectionResult.TwoPoints -> listOf(lineResult.first, lineResult.second)
        else -> emptyList()
      }
    return candidates.filter { pt ->
      val t = GeometryEngine.dot(pt - seg.a, unit)
      t >= -tolerance && t <= segLen + tolerance
    }
  }

  fun intersectCircleCircle(
    c1: Circle2D,
    c2: Circle2D,
    tolerance: Double = 1e-5,
  ): CircleIntersectionResult {
    val d = GeometryEngine.distance(c1.center, c2.center)
    if (d <= tolerance && abs(c1.radius - c2.radius) <= tolerance) {
      return CircleIntersectionResult.Coincident
    }
    if (d <= tolerance) return CircleIntersectionResult.None
    if (d > c1.radius + c2.radius + tolerance) return CircleIntersectionResult.None
    if (d < abs(c1.radius - c2.radius) - tolerance) return CircleIntersectionResult.None

    val a = (c1.radius * c1.radius - c2.radius * c2.radius + d * d) / (2.0 * d)
    val hSq = c1.radius * c1.radius - a * a
    val unit = (c2.center - c1.center) / d
    val p0 = c1.center + unit * a

    if (hSq <= tolerance * max(c1.radius, c2.radius)) {
      return CircleIntersectionResult.Tangent(p0)
    }
    val h = sqrt(max(0.0, hSq))
    val perp = Vec2(-unit.y, unit.x)
    val p1 = p0 + perp * h
    val p2 = p0 - perp * h
    return if (GeometryEngine.distance(p1, p2) <= tolerance) {
      CircleIntersectionResult.Tangent(p0)
    } else {
      CircleIntersectionResult.TwoPoints(p1, p2)
    }
  }

  fun intersectRayCircle(
    ray: Ray2D,
    circle: Circle2D,
    tolerance: Double = 1e-5,
  ): List<Vec2> {
    val unit = GeometryEngine.normalize(ray.direction) ?: return emptyList()
    val res = intersectLineCircle(Line2D(ray.origin, unit), circle, tolerance)
    val candidates =
      when (res) {
        is CircleIntersectionResult.Tangent -> listOf(res.point)
        is CircleIntersectionResult.TwoPoints -> listOf(res.first, res.second)
        else -> emptyList()
      }
    return candidates.filter { pt -> GeometryEngine.dot(pt - ray.origin, unit) >= -tolerance }
  }

  fun intersectSegmentArc(
    seg: Segment2D,
    arc: ArcPrimitive,
    tolerance: Double = 1e-5,
  ): List<Vec2> {
    val circle = Circle2D(arc.center, arc.radiusMm)
    return intersectSegmentCircle(seg, circle, tolerance).filter { pt ->
      val ang = GeometryEngine.angleDegrees(arc.center, pt)
      GeometryEngine.isAngleOnArc(ang, arc.startAngleDeg, arc.sweepAngleDeg)
    }
  }

  fun clipLineToRect(
    line: Line2D,
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
  ): Segment2D? {
    val d = GeometryEngine.normalize(line.direction) ?: return null
    val rectEdges =
      listOf(
        Segment2D(Vec2(minX, minY), Vec2(maxX, minY)),
        Segment2D(Vec2(maxX, minY), Vec2(maxX, maxY)),
        Segment2D(Vec2(maxX, maxY), Vec2(minX, maxY)),
        Segment2D(Vec2(minX, maxY), Vec2(minX, minY)),
      )
    val hits = mutableListOf<Vec2>()
    for (edge in rectEdges) {
      val eDir = edge.b - edge.a
      val denom = GeometryEngine.cross(d, eDir)
      if (abs(denom) <= 1e-9) continue
      val qp = edge.a - line.point
      val u = GeometryEngine.cross(qp, d) / denom
      if (u in -1e-6..(1.0 + 1e-6)) {
        val pt = edge.a + eDir * u.coerceIn(0.0, 1.0)
        if (hits.none { GeometryEngine.distance(it, pt) <= 1e-4 }) {
          hits.add(pt)
        }
      }
    }
    if (hits.size < 2) return null
    val sorted = hits.sortedBy { GeometryEngine.dot(it - line.point, d) }
    val first = sorted.first()
    val last = sorted.last()
    return if (GeometryEngine.distance(first, last) > 1e-4) Segment2D(first, last) else null
  }

  fun clipLineToPaper(line: Line2D, paper: PaperSpec): Segment2D? {
    return clipLineToRect(line, 0.0, 0.0, paper.widthMm, paper.heightMm)
  }

  fun clipRayToPaper(ray: Ray2D, paper: PaperSpec): Segment2D? {
    val d = GeometryEngine.normalize(ray.direction) ?: return null
    val clipped = clipLineToPaper(Line2D(ray.origin, d), paper) ?: return null
    val tA = GeometryEngine.dot(clipped.a - ray.origin, d)
    val tB = GeometryEngine.dot(clipped.b - ray.origin, d)
    val tMin = min(tA, tB)
    val tMax = max(tA, tB)
    if (tMax <= 1e-4) return null
    val startT = max(0.0, tMin)
    if (tMax - startT <= 1e-4) return null
    return Segment2D(ray.origin + d * startT, ray.origin + d * tMax)
  }

  /**
   * Computes all geometric intersection points in the document (segment-segment,
   * segment-circle, segment-arc, circle-circle) using spatial pre-filtering,
   * deduplicated within [toleranceMm].
   */
  fun computeAllIntersections(
    document: CreasePatternDocument,
    toleranceMm: Double = 1e-3,
    includeExistingEndpointTouches: Boolean = false,
  ): List<Vec2> {
    val vertexMap = document.vertices.associateBy { it.id }
    val grid = SpatialGridIndex(cellSizeMm = max(10.0, document.paper.gridSpacingMm))
    grid.index(document.vertices, document.edges)

    val result = mutableListOf<Vec2>()
    fun addUnique(pt: Vec2) {
      if (result.none { GeometryEngine.distance(it, pt) <= toleranceMm }) {
        result.add(pt)
      }
    }

    val edges = document.edges
    val seenPairs = HashSet<Long>()
    for (i in edges.indices) {
      val e1 = edges[i]
      val a1 = vertexMap[e1.startVertexId]?.position ?: continue
      val b1 = vertexMap[e1.endVertexId]?.position ?: continue
      val minPt = Vec2(min(a1.x, b1.x) - toleranceMm, min(a1.y, b1.y) - toleranceMm)
      val maxPt = Vec2(max(a1.x, b1.x) + toleranceMm, max(a1.y, b1.y) + toleranceMm)
      val candidates = grid.queryEdgesInBox(minPt, maxPt)

      for (e2 in candidates) {
        if (e1.id >= e2.id) continue
        if (!includeExistingEndpointTouches &&
          (e1.startVertexId == e2.startVertexId ||
            e1.startVertexId == e2.endVertexId ||
            e1.endVertexId == e2.startVertexId ||
            e1.endVertexId == e2.endVertexId)
        ) {
          continue
        }
        val pairHash = (e1.id.hashCode().toLong() shl 32) xor e2.id.hashCode().toLong()
        if (!seenPairs.add(pairHash)) continue

        val a2 = vertexMap[e2.startVertexId]?.position ?: continue
        val b2 = vertexMap[e2.endVertexId]?.position ?: continue
        when (val res = intersectSegmentSegment(Segment2D(a1, b1), Segment2D(a2, b2), toleranceMm)) {
          is SegmentIntersectionResult.Point -> addUnique(res.point)
          else -> Unit
        }
      }
    }

    // Segment vs Circle
    for (edge in edges) {
      val a = vertexMap[edge.startVertexId]?.position ?: continue
      val b = vertexMap[edge.endVertexId]?.position ?: continue
      val seg = Segment2D(a, b)
      for (circle in document.circles) {
        val pts = intersectSegmentCircle(seg, Circle2D(circle.center, circle.radiusMm), toleranceMm)
        pts.forEach(::addUnique)
      }
      for (arc in document.arcs) {
        val pts = intersectSegmentArc(seg, arc, toleranceMm)
        pts.forEach(::addUnique)
      }
    }

    // Circle vs Circle
    for (i in document.circles.indices) {
      for (j in i + 1 until document.circles.size) {
        val c1 = document.circles[i]
        val c2 = document.circles[j]
        when (
          val res =
            intersectCircleCircle(
              Circle2D(c1.center, c1.radiusMm),
              Circle2D(c2.center, c2.radiusMm),
              toleranceMm,
            )
        ) {
          is CircleIntersectionResult.Tangent -> addUnique(res.point)
          is CircleIntersectionResult.TwoPoints -> {
            addUnique(res.first)
            addUnique(res.second)
          }
          else -> Unit
        }
      }
    }

    return result
  }
}
