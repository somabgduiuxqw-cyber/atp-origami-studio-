package com.example.core.model

enum class FoldAssignment(val foldCode: String, val label: String) {
  MOUNTAIN("M", "Mountain"),
  VALLEY("V", "Valley"),
  BOUNDARY("B", "Boundary"),
  UNASSIGNED("U", "Unassigned"),
  CONSTRUCTION("C", "Construct"),
}

data class Vec2(val x: Double, val y: Double) {
  operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

  operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

  operator fun times(scalar: Double): Vec2 = Vec2(x * scalar, y * scalar)

  operator fun div(scalar: Double): Vec2 = Vec2(x / scalar, y / scalar)
}

data class Vertex(
  val id: String,
  val position: Vec2,
  val isLocked: Boolean = false,
)

data class Edge(
  val id: String,
  val startVertexId: String,
  val endVertexId: String,
  val assignment: FoldAssignment,
)

data class CirclePrimitive(
  val id: String,
  val center: Vec2,
  val radiusMm: Double,
  val assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
)

data class ArcPrimitive(
  val id: String,
  val center: Vec2,
  val radiusMm: Double,
  val startAngleDeg: Double,
  val sweepAngleDeg: Double,
  val assignment: FoldAssignment = FoldAssignment.CONSTRUCTION,
)

data class PaperSpec(
  val widthMm: Double = 200.0,
  val heightMm: Double = 200.0,
  val gridSpacingMm: Double = 25.0,
)

data class GeometrySettings(
  val snapDistancePx: Double = 22.0,
  val angleSnapEnabled: Boolean = false,
  val angleSnapStepDeg: Double = 22.5,
  val coordinatePrecision: Int = 1,
  val showConstructionLines: Boolean = true,
  val showIntersections: Boolean = true,
  val geometryToleranceMm: Double = 0.01,
)

data class CreasePatternDocument(
  val id: String,
  val name: String,
  val paper: PaperSpec,
  val vertices: List<Vertex>,
  val edges: List<Edge>,
  val createdAt: Long,
  val updatedAt: Long,
  val formatVersion: Int = 2,
  val circles: List<CirclePrimitive> = emptyList(),
  val arcs: List<ArcPrimitive> = emptyList(),
) {
  fun vertexById(id: String): Vertex? = vertices.firstOrNull { it.id == id }
}
