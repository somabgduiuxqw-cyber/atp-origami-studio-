package com.example.core.model

enum class FoldAssignment(val foldCode: String, val label: String) {
  MOUNTAIN("M", "Mountain"),
  VALLEY("V", "Valley"),
  BOUNDARY("B", "Boundary"),
  UNASSIGNED("U", "Unassigned"),
  CONSTRUCTION("C", "Construct"),
}

data class Vec2(val x: Double, val y: Double)

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

data class PaperSpec(
  val widthMm: Double = 200.0,
  val heightMm: Double = 200.0,
  val gridSpacingMm: Double = 25.0,
)

data class CreasePatternDocument(
  val id: String,
  val name: String,
  val paper: PaperSpec,
  val vertices: List<Vertex>,
  val edges: List<Edge>,
  val createdAt: Long,
  val updatedAt: Long,
  val formatVersion: Int = 1,
) {
  fun vertexById(id: String): Vertex? = vertices.firstOrNull { it.id == id }
}
