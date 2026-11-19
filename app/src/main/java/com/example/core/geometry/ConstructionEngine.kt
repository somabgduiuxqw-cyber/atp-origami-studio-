package com.example.core.geometry

import com.example.core.model.ArcPrimitive
import com.example.core.model.CirclePrimitive
import com.example.core.model.FoldAssignment
import com.example.core.model.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object ConstructionEngine {

  fun parallelLineThrough(point: Vec2, refA: Vec2, refB: Vec2): Line2D? {
    val dir = GeometryEngine.normalize(refB - refA) ?: return null
    return Line2D(point = point, direction = dir)
  }

  fun perpendicularLineThrough(point: Vec2, refA: Vec2, refB: Vec2): Line2D? {
    val perp = GeometryEngine.perpendicularUnit(refB - refA) ?: return null
    return Line2D(point = point, direction = perp)
  }

  fun angleBisector(vertex: Vec2, leg1Point: Vec2, leg2Point: Vec2): Ray2D? {
    val u1 = GeometryEngine.normalize(leg1Point - vertex) ?: return null
    val u2 = GeometryEngine.normalize(leg2Point - vertex) ?: return null
    val sum = u1 + u2
    val sumLen = GeometryEngine.length(sum)
    if (sumLen <= 1e-6) {
      // Straight angle (180 degrees): bisector is perpendicular to u1
      val perp = Vec2(-u1.y, u1.x)
      return Ray2D(origin = vertex, direction = perp)
    }
    return Ray2D(origin = vertex, direction = sum / sumLen)
  }

  fun circleFromCenterAndRadius(
    center: Vec2,
    radiusMm: Double,
    assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
  ): CirclePrimitive? {
    if (radiusMm <= 1e-4) return null
    return CirclePrimitive(
      id = GeometryEngine.newId(),
      center = center,
      radiusMm = radiusMm,
      assignment = assignment,
    )
  }

  fun circleFromTwoPoints(
    p1: Vec2,
    p2: Vec2,
    assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
  ): CirclePrimitive? {
    val diameter = GeometryEngine.distance(p1, p2)
    if (diameter <= 1e-4) return null
    val center = GeometryEngine.midpoint(p1, p2)
    return CirclePrimitive(
      id = GeometryEngine.newId(),
      center = center,
      radiusMm = diameter * 0.5,
      assignment = assignment,
    )
  }

  fun circleThroughThreePoints(
    p1: Vec2,
    p2: Vec2,
    p3: Vec2,
    assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
  ): CirclePrimitive? {
    val d = 2.0 * (p1.x * (p2.y - p3.y) + p2.x * (p3.y - p1.y) + p3.x * (p1.y - p2.y))
    if (abs(d) <= 1e-6) return null // collinear

    val p1Sq = p1.x * p1.x + p1.y * p1.y
    val p2Sq = p2.x * p2.x + p2.y * p2.y
    val p3Sq = p3.x * p3.x + p3.y * p3.y

    val ux = (p1Sq * (p2.y - p3.y) + p2Sq * (p3.y - p1.y) + p3Sq * (p1.y - p2.y)) / d
    val uy = (p1Sq * (p3.x - p2.x) + p2Sq * (p1.x - p3.x) + p3Sq * (p2.x - p1.x)) / d
    val center = Vec2(ux, uy)
    val radius = GeometryEngine.distance(center, p1)
    if (radius <= 1e-4 || radius > 1e5) return null
    return CirclePrimitive(
      id = GeometryEngine.newId(),
      center = center,
      radiusMm = radius,
      assignment = assignment,
    )
  }

  fun arcFromCenterStartEnd(
    center: Vec2,
    startPoint: Vec2,
    endPoint: Vec2,
    assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
  ): ArcPrimitive? {
    val radius = GeometryEngine.distance(center, startPoint)
    if (radius <= 1e-4) return null
    val startDeg = GeometryEngine.angleDegrees(center, startPoint)
    val endDeg = GeometryEngine.angleDegrees(center, endPoint)
    var sweep = GeometryEngine.normalizeAngleDeg(endDeg - startDeg)
    if (sweep < 1e-3) sweep = 360.0
    return ArcPrimitive(
      id = GeometryEngine.newId(),
      center = center,
      radiusMm = radius,
      startAngleDeg = startDeg,
      sweepAngleDeg = sweep,
      assignment = assignment,
    )
  }

  fun rectangleVertices(cornerA: Vec2, cornerB: Vec2): List<Vec2> {
    val minX = minOf(cornerA.x, cornerB.x)
    val maxX = maxOf(cornerA.x, cornerB.x)
    val minY = minOf(cornerA.y, cornerB.y)
    val maxY = maxOf(cornerA.y, cornerB.y)
    if (maxX - minX <= 1e-4 || maxY - minY <= 1e-4) return emptyList()
    return listOf(
      Vec2(minX, minY),
      Vec2(maxX, minY),
      Vec2(maxX, maxY),
      Vec2(minX, maxY),
    )
  }

  fun regularPolygonVertices(
    center: Vec2,
    circumradiusPoint: Vec2,
    sides: Int,
  ): List<Vec2> {
    if (sides < 3) return emptyList()
    val radius = GeometryEngine.distance(center, circumradiusPoint)
    if (radius <= 1e-4) return emptyList()
    val startRad = kotlin.math.atan2(circumradiusPoint.y - center.y, circumradiusPoint.x - center.x)
    val stepRad = 2.0 * Math.PI / sides
    return (0 until sides).map { i ->
      val ang = startRad + i * stepRad
      Vec2(center.x + radius * cos(ang), center.y + radius * sin(ang))
    }
  }
}
