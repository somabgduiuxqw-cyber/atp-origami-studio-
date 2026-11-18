package com.example.data.model

import com.example.core.geometry.GeometryEngine
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import kotlin.math.sqrt

enum class OrigamiTemplateId {
  BLANK,
  WATERBOMB_BASE,
  PRELIMINARY_BASE,
  FISH_BASE,
  BIRD_BASE,
}

data class OrigamiTemplate(
  val id: OrigamiTemplateId,
  val title: String,
  val subtitle: String,
  val defaultPaper: PaperSpec = PaperSpec(200.0, 200.0, 25.0),
)

object TemplateCatalog {
  val templates: List<OrigamiTemplate> =
    listOf(
      OrigamiTemplate(
        id = OrigamiTemplateId.BLANK,
        title = "Blank Sheet",
        subtitle = "Clean boundary sheet",
      ),
      OrigamiTemplate(
        id = OrigamiTemplateId.PRELIMINARY_BASE,
        title = "Preliminary Base",
        subtitle = "Classic square base",
      ),
      OrigamiTemplate(
        id = OrigamiTemplateId.WATERBOMB_BASE,
        title = "Waterbomb Base",
        subtitle = "Balloon fold base",
      ),
      OrigamiTemplate(
        id = OrigamiTemplateId.FISH_BASE,
        title = "Fish Base",
        subtitle = "Kite + 22.5° flaps",
      ),
      OrigamiTemplate(
        id = OrigamiTemplateId.BIRD_BASE,
        title = "Bird Base",
        subtitle = "Crane foundation CP",
      ),
    )

  fun buildDocument(
    templateId: OrigamiTemplateId,
    projectId: String,
    projectName: String,
    paper: PaperSpec,
    now: Long = System.currentTimeMillis(),
  ): CreasePatternDocument {
    val builder = CreaseBuilder(paper.widthMm, paper.heightMm)
    builder.addBoundaryBox()

    when (templateId) {
      OrigamiTemplateId.BLANK -> Unit
      OrigamiTemplateId.PRELIMINARY_BASE -> {
        // Axial mountains, diagonal valleys
        builder.addCrease(0.5, 0.0, 0.5, 1.0, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.0, 0.5, 1.0, 0.5, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.0, 0.0, 1.0, 1.0, FoldAssignment.VALLEY)
        builder.addCrease(1.0, 0.0, 0.0, 1.0, FoldAssignment.VALLEY)
      }
      OrigamiTemplateId.WATERBOMB_BASE -> {
        // Axial valleys, diagonal mountains
        builder.addCrease(0.5, 0.0, 0.5, 1.0, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 0.5, 1.0, 0.5, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 0.0, 1.0, 1.0, FoldAssignment.MOUNTAIN)
        builder.addCrease(1.0, 0.0, 0.0, 1.0, FoldAssignment.MOUNTAIN)
      }
      OrigamiTemplateId.FISH_BASE -> {
        val k = sqrt(2.0) - 1.0 // ~0.41421356
        val mid = 0.5
        val center = (1.0 - k / 2.0) // intersection along main diagonal
        // Main diagonal valley
        builder.addCrease(0.0, 0.0, 1.0, 1.0, FoldAssignment.VALLEY)
        // Kite creases from top-left (0,0) and bottom-right (1,1)
        builder.addCrease(0.0, 0.0, 1.0, k, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 0.0, k, 1.0, FoldAssignment.VALLEY)
        builder.addCrease(1.0, 1.0, 1.0 - k, 0.0, FoldAssignment.VALLEY)
        builder.addCrease(1.0, 1.0, 0.0, 1.0 - k, FoldAssignment.VALLEY)
        // Mountain bisectors for fish fins
        builder.addCrease(0.0, 1.0, mid * (1.0 - k / 2.0), 1.0 - mid * (1.0 - k / 2.0), FoldAssignment.MOUNTAIN)
        builder.addCrease(1.0, 0.0, 1.0 - mid * (1.0 - k / 2.0), mid * (1.0 - k / 2.0), FoldAssignment.MOUNTAIN)
        builder.addCrease(0.0, 1.0, 1.0, 0.0, FoldAssignment.CONSTRUCTION)
        // Subtle rabbit-ear interior fold
        builder.addCrease(0.5, 0.5, center - 0.5 + 0.5, 0.5, FoldAssignment.UNASSIGNED)
      }
      OrigamiTemplateId.BIRD_BASE -> {
        val k = sqrt(2.0) - 1.0 // tan(22.5°) = sqrt(2) - 1
        // Preliminary base diagonals + axial
        builder.addCrease(0.0, 0.0, 1.0, 1.0, FoldAssignment.MOUNTAIN)
        builder.addCrease(1.0, 0.0, 0.0, 1.0, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.5, 0.0, 0.5, 1.0, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 0.5, 1.0, 0.5, FoldAssignment.VALLEY)
        // Petal fold 22.5° creases around all four corners toward center
        val corners = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        // Top-left (0,0)
        builder.addCrease(0.0, 0.0, 0.5, 0.5 * k, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 0.0, 0.5 * k, 0.5, FoldAssignment.VALLEY)
        // Top-right (1,0)
        builder.addCrease(1.0, 0.0, 0.5, 0.5 * k, FoldAssignment.VALLEY)
        builder.addCrease(1.0, 0.0, 1.0 - 0.5 * k, 0.5, FoldAssignment.VALLEY)
        // Bottom-right (1,1)
        builder.addCrease(1.0, 1.0, 0.5, 1.0 - 0.5 * k, FoldAssignment.VALLEY)
        builder.addCrease(1.0, 1.0, 1.0 - 0.5 * k, 0.5, FoldAssignment.VALLEY)
        // Bottom-left (0,1)
        builder.addCrease(0.0, 1.0, 0.5, 1.0 - 0.5 * k, FoldAssignment.VALLEY)
        builder.addCrease(0.0, 1.0, 0.5 * k, 0.5, FoldAssignment.VALLEY)
        // Inner diamond mountains
        val d = 0.5 * k
        builder.addCrease(0.5, d, d, 0.5, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.5, d, 1.0 - d, 0.5, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.5, 1.0 - d, d, 0.5, FoldAssignment.MOUNTAIN)
        builder.addCrease(0.5, 1.0 - d, 1.0 - d, 0.5, FoldAssignment.MOUNTAIN)
        corners.size // suppress unused
      }
    }

    val (vertices, edges) = builder.build()
    return CreasePatternDocument(
      id = projectId,
      name = projectName,
      paper = paper,
      vertices = vertices,
      edges = edges,
      createdAt = now,
      updatedAt = now,
    )
  }

  private class CreaseBuilder(private val widthMm: Double, private val heightMm: Double) {
    private val vertices = mutableListOf<Vertex>()
    private val edges = mutableListOf<Edge>()

    fun addBoundaryBox() {
      val tl = getOrCreateNormalized(0.0, 0.0)
      val tr = getOrCreateNormalized(1.0, 0.0)
      val br = getOrCreateNormalized(1.0, 1.0)
      val bl = getOrCreateNormalized(0.0, 1.0)
      addEdgeIfAbsent(tl.id, tr.id, FoldAssignment.BOUNDARY)
      addEdgeIfAbsent(tr.id, br.id, FoldAssignment.BOUNDARY)
      addEdgeIfAbsent(br.id, bl.id, FoldAssignment.BOUNDARY)
      addEdgeIfAbsent(bl.id, tl.id, FoldAssignment.BOUNDARY)
    }

    fun addCrease(
      nx1: Double,
      ny1: Double,
      nx2: Double,
      ny2: Double,
      assignment: FoldAssignment,
    ) {
      val v1 = getOrCreateNormalized(nx1, ny1)
      val v2 = getOrCreateNormalized(nx2, ny2)
      if (v1.id != v2.id) {
        addEdgeIfAbsent(v1.id, v2.id, assignment)
      }
    }

    private fun getOrCreateNormalized(nx: Double, ny: Double): Vertex {
      val pos = Vec2(nx * widthMm, ny * heightMm)
      val existing = GeometryEngine.findDuplicateVertex(vertices, pos, toleranceMm = 1e-3)
      if (existing != null) return existing
      val v = Vertex(id = GeometryEngine.newId(), position = pos)
      vertices.add(v)
      return v
    }

    private fun addEdgeIfAbsent(v1Id: String, v2Id: String, assignment: FoldAssignment) {
      if (GeometryEngine.findDuplicateEdge(edges, v1Id, v2Id) == null) {
        edges.add(Edge(GeometryEngine.newId(), v1Id, v2Id, assignment))
      }
    }

    fun build(): Pair<List<Vertex>, List<Edge>> = vertices.toList() to edges.toList()
  }
}
