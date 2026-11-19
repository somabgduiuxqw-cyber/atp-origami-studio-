package com.example.data.serializers

import com.example.core.model.ArcPrimitive
import com.example.core.model.CirclePrimitive
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class PaperSpecDto(
  val widthMm: Double,
  val heightMm: Double,
  val gridSpacingMm: Double,
)

@JsonClass(generateAdapter = true)
data class VertexDto(
  val id: String,
  val x: Double,
  val y: Double,
  val isLocked: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class EdgeDto(
  val id: String,
  val startVertexId: String,
  val endVertexId: String,
  val assignment: String,
)

@JsonClass(generateAdapter = true)
data class CircleDto(
  val id: String,
  val cx: Double,
  val cy: Double,
  val radiusMm: Double,
  val assignment: String = FoldAssignment.CONSTRUCTION.name,
)

@JsonClass(generateAdapter = true)
data class ArcDto(
  val id: String,
  val cx: Double,
  val cy: Double,
  val radiusMm: Double,
  val startAngleDeg: Double,
  val sweepAngleDeg: Double,
  val assignment: String = FoldAssignment.CONSTRUCTION.name,
)

@JsonClass(generateAdapter = true)
data class CreasePatternDocumentDto(
  val formatVersion: Int = 1,
  val id: String,
  val name: String,
  val paper: PaperSpecDto,
  val vertices: List<VertexDto>,
  val edges: List<EdgeDto>,
  val createdAt: Long,
  val updatedAt: Long,
  val circles: List<CircleDto> = emptyList(),
  val arcs: List<ArcDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PreviewEdgeDto(
  val x1: Float,
  val y1: Float,
  val x2: Float,
  val y2: Float,
  val assignment: String,
)

@JsonClass(generateAdapter = true)
data class PreviewCircleDto(
  val cx: Float,
  val cy: Float,
  val radiusMm: Float,
  val assignment: String,
)

@JsonClass(generateAdapter = true)
data class PreviewSnapshotDto(
  val widthMm: Float,
  val heightMm: Float,
  val edges: List<PreviewEdgeDto>,
  val circles: List<PreviewCircleDto> = emptyList(),
)

class ProjectJsonSerializer {
  private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val documentAdapter = moshi.adapter(CreasePatternDocumentDto::class.java).indent("  ")
  private val previewAdapter = moshi.adapter(PreviewSnapshotDto::class.java)

  fun serialize(document: CreasePatternDocument): String {
    val dto =
      CreasePatternDocumentDto(
        formatVersion = maxOf(document.formatVersion, 2),
        id = document.id,
        name = document.name,
        paper =
          PaperSpecDto(
            widthMm = document.paper.widthMm,
            heightMm = document.paper.heightMm,
            gridSpacingMm = document.paper.gridSpacingMm,
          ),
        vertices =
          document.vertices.map {
            VertexDto(id = it.id, x = it.position.x, y = it.position.y, isLocked = it.isLocked)
          },
        edges =
          document.edges.map {
            EdgeDto(
              id = it.id,
              startVertexId = it.startVertexId,
              endVertexId = it.endVertexId,
              assignment = it.assignment.name,
            )
          },
        createdAt = document.createdAt,
        updatedAt = document.updatedAt,
        circles =
          document.circles.map {
            CircleDto(
              id = it.id,
              cx = it.center.x,
              cy = it.center.y,
              radiusMm = it.radiusMm,
              assignment = it.assignment.name,
            )
          },
        arcs =
          document.arcs.map {
            ArcDto(
              id = it.id,
              cx = it.center.x,
              cy = it.center.y,
              radiusMm = it.radiusMm,
              startAngleDeg = it.startAngleDeg,
              sweepAngleDeg = it.sweepAngleDeg,
              assignment = it.assignment.name,
            )
          },
      )
    return documentAdapter.toJson(dto)
  }

  fun deserialize(json: String): CreasePatternDocument? {
    val dto = runCatching { documentAdapter.fromJson(json) }.getOrNull() ?: return null
    val vertices =
      dto.vertices.map {
        Vertex(id = it.id, position = Vec2(it.x, it.y), isLocked = it.isLocked)
      }
    val validVertexIds = vertices.map { it.id }.toSet()
    val edges =
      dto.edges.mapNotNull { edgeDto ->
        if (edgeDto.startVertexId !in validVertexIds || edgeDto.endVertexId !in validVertexIds) {
          null
        } else {
          val assignment =
            runCatching { FoldAssignment.valueOf(edgeDto.assignment) }
              .getOrDefault(FoldAssignment.UNASSIGNED)
          Edge(
            id = edgeDto.id,
            startVertexId = edgeDto.startVertexId,
            endVertexId = edgeDto.endVertexId,
            assignment = assignment,
          )
        }
      }
    val circles =
      dto.circles.map { c ->
        val assignment =
          runCatching { FoldAssignment.valueOf(c.assignment) }
            .getOrDefault(FoldAssignment.CONSTRUCTION)
        CirclePrimitive(
          id = c.id,
          center = Vec2(c.cx, c.cy),
          radiusMm = c.radiusMm,
          assignment = assignment,
        )
      }
    val arcs =
      dto.arcs.map { a ->
        val assignment =
          runCatching { FoldAssignment.valueOf(a.assignment) }
            .getOrDefault(FoldAssignment.CONSTRUCTION)
        ArcPrimitive(
          id = a.id,
          center = Vec2(a.cx, a.cy),
          radiusMm = a.radiusMm,
          startAngleDeg = a.startAngleDeg,
          sweepAngleDeg = a.sweepAngleDeg,
          assignment = assignment,
        )
      }
    return CreasePatternDocument(
      id = dto.id,
      name = dto.name,
      paper =
        PaperSpec(
          widthMm = dto.paper.widthMm,
          heightMm = dto.paper.heightMm,
          gridSpacingMm = dto.paper.gridSpacingMm,
        ),
      vertices = vertices,
      edges = edges,
      createdAt = dto.createdAt,
      updatedAt = dto.updatedAt,
      formatVersion = dto.formatVersion,
      circles = circles,
      arcs = arcs,
    )
  }

  fun serializePreview(document: CreasePatternDocument): String {
    val vertexMap = document.vertices.associateBy { it.id }
    val previewEdges =
      document.edges.mapNotNull { edge ->
        val a = vertexMap[edge.startVertexId]?.position ?: return@mapNotNull null
        val b = vertexMap[edge.endVertexId]?.position ?: return@mapNotNull null
        PreviewEdgeDto(
          x1 = a.x.toFloat(),
          y1 = a.y.toFloat(),
          x2 = b.x.toFloat(),
          y2 = b.y.toFloat(),
          assignment = edge.assignment.name,
        )
      }
    val previewCircles =
      document.circles.map { c ->
        PreviewCircleDto(
          cx = c.center.x.toFloat(),
          cy = c.center.y.toFloat(),
          radiusMm = c.radiusMm.toFloat(),
          assignment = c.assignment.name,
        )
      }
    return previewAdapter.toJson(
      PreviewSnapshotDto(
        widthMm = document.paper.widthMm.toFloat(),
        heightMm = document.paper.heightMm.toFloat(),
        edges = previewEdges,
        circles = previewCircles,
      )
    )
  }

  fun deserializePreview(json: String): PreviewSnapshotDto? {
    if (json.isBlank()) return null
    return runCatching { previewAdapter.fromJson(json) }.getOrNull()
  }
}
