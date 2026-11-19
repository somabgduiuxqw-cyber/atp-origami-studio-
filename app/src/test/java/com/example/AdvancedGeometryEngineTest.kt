package com.example

import com.example.core.geometry.Circle2D
import com.example.core.geometry.CircleIntersectionResult
import com.example.core.geometry.ConstructionEngine
import com.example.core.geometry.CreaseEditingEngine
import com.example.core.geometry.GeometryEngine
import com.example.core.geometry.IntersectionEngine
import com.example.core.geometry.Line2D
import com.example.core.geometry.LineIntersectionResult
import com.example.core.geometry.Ray2D
import com.example.core.geometry.Segment2D
import com.example.core.geometry.SegmentIntersectionResult
import com.example.core.geometry.SymmetrySpec
import com.example.core.geometry.TransformEngine
import com.example.core.history.DocumentSnapshotCommand
import com.example.core.history.HistoryManager
import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import com.example.core.snapping.SnapConfig
import com.example.core.snapping.SnapEngine
import com.example.core.snapping.SnapKind
import com.example.data.serializers.ProjectJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGeometryEngineTest {

  private fun emptyDoc(width: Double = 200.0, height: Double = 200.0): CreasePatternDocument {
    return CreasePatternDocument(
      id = "test-doc",
      name = "Test",
      paper = PaperSpec(width, height, 25.0),
      vertices = emptyList(),
      edges = emptyList(),
      createdAt = 1L,
      updatedAt = 1L,
    )
  }

  @Test
  fun `line and ray intersections handle crossing parallel and coincident cases`() {
    val l1 = Line2D(Vec2(0.0, 0.0), Vec2(1.0, 1.0))
    val l2 = Line2D(Vec2(0.0, 100.0), Vec2(1.0, -1.0))
    val hit = IntersectionEngine.intersectLineLine(l1, l2)
    assertTrue(hit is LineIntersectionResult.Point)
    val pt = (hit as LineIntersectionResult.Point).point
    assertEquals(50.0, pt.x, 1e-6)
    assertEquals(50.0, pt.y, 1e-6)

    val parallel = IntersectionEngine.intersectLineLine(l1, Line2D(Vec2(0.0, 10.0), Vec2(1.0, 1.0)))
    assertTrue(parallel is LineIntersectionResult.Parallel)

    val coincident = IntersectionEngine.intersectLineLine(l1, Line2D(Vec2(50.0, 50.0), Vec2(-2.0, -2.0)))
    assertTrue(coincident is LineIntersectionResult.Coincident)

    val rayHit = IntersectionEngine.intersectRayLine(Ray2D(Vec2(0.0, 50.0), Vec2(1.0, 0.0)), Line2D(Vec2(80.0, 0.0), Vec2(0.0, 1.0)))
    assertTrue(rayHit is LineIntersectionResult.Point)
    assertEquals(80.0, (rayHit as LineIntersectionResult.Point).point.x, 1e-6)

    val rayBehind = IntersectionEngine.intersectRayLine(Ray2D(Vec2(100.0, 50.0), Vec2(1.0, 0.0)), Line2D(Vec2(80.0, 0.0), Vec2(0.0, 1.0)))
    assertTrue(rayBehind is LineIntersectionResult.Parallel)
  }

  @Test
  fun `circle intersections handle two points tangent and segment intersections`() {
    val c1 = Circle2D(Vec2(0.0, 0.0), 50.0)
    val c2 = Circle2D(Vec2(80.0, 0.0), 50.0)
    val twoPts = IntersectionEngine.intersectCircleCircle(c1, c2)
    assertTrue(twoPts is CircleIntersectionResult.TwoPoints)
    val p1 = (twoPts as CircleIntersectionResult.TwoPoints).first
    val p2 = twoPts.second
    assertEquals(40.0, p1.x, 1e-5)
    assertEquals(40.0, p2.x, 1e-5)
    assertEquals(30.0, kotlin.math.abs(p1.y), 1e-5)

    val tangent = IntersectionEngine.intersectCircleCircle(Circle2D(Vec2(0.0, 0.0), 50.0), Circle2D(Vec2(100.0, 0.0), 50.0))
    assertTrue(tangent is CircleIntersectionResult.Tangent)
    val tp = (tangent as CircleIntersectionResult.Tangent).point
    assertEquals(50.0, tp.x, 1e-5)
    assertEquals(0.0, tp.y, 1e-5)

    val segHits = IntersectionEngine.intersectSegmentCircle(Segment2D(Vec2(-100.0, 0.0), Vec2(100.0, 0.0)), c1)
    assertEquals(2, segHits.size)
  }

  @Test
  fun `parallel perpendicular bisector and midpoint constructions are exact`() {
    val par = ConstructionEngine.parallelLineThrough(Vec2(10.0, 50.0), Vec2(0.0, 0.0), Vec2(100.0, 0.0))
    assertNotNull(par)
    assertEquals(0.0, par!!.direction.y, 1e-6)

    val perp = ConstructionEngine.perpendicularLineThrough(Vec2(40.0, 50.0), Vec2(0.0, 0.0), Vec2(100.0, 0.0))
    assertNotNull(perp)
    assertEquals(0.0, perp!!.direction.x, 1e-6)

    val bisector = ConstructionEngine.angleBisector(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 100.0))
    assertNotNull(bisector)
    assertEquals(bisector!!.direction.x, bisector.direction.y, 1e-6)

    val mid = GeometryEngine.midpoint(Vec2(20.0, 40.0), Vec2(80.0, 100.0))
    assertEquals(50.0, mid.x, 1e-6)
    assertEquals(70.0, mid.y, 1e-6)

    val circum = ConstructionEngine.circleThroughThreePoints(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 100.0))
    assertNotNull(circum)
    assertEquals(50.0, circum!!.center.x, 1e-5)
    assertEquals(50.0, circum.center.y, 1e-5)
  }

  @Test
  fun `snapping supports intersections midpoints centers perpendicular and angle snap`() {
    val v1 = Vertex("v1", Vec2(0.0, 0.0))
    val v2 = Vertex("v2", Vec2(200.0, 200.0))
    val v3 = Vertex("v3", Vec2(0.0, 200.0))
    val v4 = Vertex("v4", Vec2(200.0, 0.0))
    val e1 = Edge("e1", "v1", "v2", FoldAssignment.MOUNTAIN)
    val e2 = Edge("e2", "v3", "v4", FoldAssignment.VALLEY)
    val doc = emptyDoc().copy(vertices = listOf(v1, v2, v3, v4), edges = listOf(e1, e2))

    val config = SnapConfig(
      snapToGrid = true,
      snapToVertex = true,
      snapToEndpoint = true,
      snapToIntersection = true,
      snapToMidpoint = true,
      snapToPerpendicular = true,
      snapToCenter = true,
      snapToNearestEdge = true,
      gridSpacingMm = 25.0,
      snapRadiusMm = 8.0,
    )

    val intersectionSnap = SnapEngine.resolveSnap(Vec2(101.0, 99.5), doc, config)
    assertEquals(SnapKind.INTERSECTION, intersectionSnap.kind)
    assertEquals(100.0, intersectionSnap.position.x, 1e-4)
    assertEquals(100.0, intersectionSnap.position.y, 1e-4)

    // Midpoint snap on single horizontal edge
    val hDoc = emptyDoc().copy(
      vertices = listOf(Vertex("a", Vec2(10.0, 10.0)), Vertex("b", Vec2(90.0, 10.0))),
      edges = listOf(Edge("e", "a", "b", FoldAssignment.MOUNTAIN)),
    )
    val midSnap = SnapEngine.resolveSnap(Vec2(51.0, 11.0), hDoc, config)
    assertEquals(SnapKind.MIDPOINT, midSnap.kind)
    assertEquals(50.0, midSnap.position.x, 1e-4)
  }

  @Test
  fun `transformations symmetry and undo redo preserve geometry and fold assignments`() {
    var doc = emptyDoc()
    doc = TransformEngine.addSegmentWithDeduplication(doc, Vec2(20.0, 20.0), Vec2(80.0, 20.0), FoldAssignment.MOUNTAIN)
    assertEquals(1, doc.edges.size)

    val symDoc =
      TransformEngine.applySymmetry(
        doc = doc,
        selectedVertexIds = emptySet(),
        selectedEdgeIds = setOf(doc.edges.first().id),
        symmetry = SymmetrySpec.VerticalCenter,
      )
    assertEquals(2, symDoc.edges.size)
    assertTrue(symDoc.edges.all { it.assignment == FoldAssignment.MOUNTAIN })
    val mirroredVerts = symDoc.vertices.map { it.position.x }.sorted()
    assertEquals(120.0, mirroredVerts[2], 1e-4)
    assertEquals(180.0, mirroredVerts[3], 1e-4)

    // Rotational 4-fold symmetry from 1 crease -> 4 creases
    val rot4Doc =
      TransformEngine.applySymmetry(
        doc = doc,
        selectedVertexIds = emptySet(),
        selectedEdgeIds = setOf(doc.edges.first().id),
        symmetry = SymmetrySpec.Rotational(Vec2(100.0, 100.0), 4),
      )
    assertEquals(4, rot4Doc.edges.size)

    // Undo/Redo via DocumentSnapshotCommand
    val history = HistoryManager()
    val afterCmd = history.execute(doc, DocumentSnapshotCommand("Symmetry", doc, rot4Doc))
    assertEquals(4, afterCmd.edges.size)
    val undone = history.undo(afterCmd)
    assertEquals(1, undone.edges.size)
    val redone = history.redo(undone)
    assertEquals(4, redone.edges.size)
  }

  @Test
  fun `edge splitting joining trimming extending and vertex merging work accurately`() {
    var doc = emptyDoc()
    doc = TransformEngine.addSegmentWithDeduplication(doc, Vec2(0.0, 100.0), Vec2(200.0, 100.0), FoldAssignment.MOUNTAIN)
    doc = TransformEngine.addSegmentWithDeduplication(doc, Vec2(100.0, 0.0), Vec2(100.0, 200.0), FoldAssignment.VALLEY)

    // Split all intersections -> 2 crossing edges become 4 edges sharing center vertex (100,100)
    val splitDoc = CreaseEditingEngine.splitAllEdgesAtIntersections(doc, toleranceMm = 0.01)
    assertEquals(5, splitDoc.vertices.size)
    assertEquals(4, splitDoc.edges.size)

    // Delete vertical segments and join the 2 collinear mountain halves back into 1 edge
    val mountainEdges = splitDoc.edges.filter { it.assignment == FoldAssignment.MOUNTAIN }
    val onlyHorizontal = splitDoc.copy(edges = mountainEdges)
    val joinedDoc = CreaseEditingEngine.joinCompatibleEdges(onlyHorizontal, mountainEdges[0].id, mountainEdges[1].id)
    assertEquals(1, joinedDoc.edges.size)
    assertEquals(4, joinedDoc.vertices.size)

    // Merge nearby vertices
    val messyDoc =
      emptyDoc().copy(
        vertices = listOf(Vertex("v1", Vec2(10.0, 10.0)), Vertex("v2", Vec2(10.2, 10.1)), Vertex("v3", Vec2(100.0, 10.0))),
        edges = listOf(Edge("e1", "v1", "v3", FoldAssignment.VALLEY), Edge("e2", "v2", "v3", FoldAssignment.VALLEY)),
      )
    val merged = CreaseEditingEngine.mergeNearbyVertices(messyDoc, toleranceMm = 0.5)
    assertEquals(2, merged.vertices.size)
    assertEquals(1, merged.edges.size)
  }

  @Test
  fun `json serializer loads Version 0_1 documents seamlessly`() {
    val v1Json =
      """
      {
        "formatVersion": 1,
        "id": "legacy-1",
        "name": "Legacy V1 Pattern",
        "paper": { "widthMm": 200.0, "heightMm": 200.0, "gridSpacingMm": 25.0 },
        "vertices": [
          { "id": "a", "x": 0.0, "y": 0.0 },
          { "id": "b", "x": 200.0, "y": 200.0 }
        ],
        "edges": [
          { "id": "e1", "startVertexId": "a", "endVertexId": "b", "assignment": "VALLEY" }
        ],
        "createdAt": 1000,
        "updatedAt": 2000
      }
      """.trimIndent()

    val serializer = ProjectJsonSerializer()
    val loaded = serializer.deserialize(v1Json)
    assertNotNull(loaded)
    assertEquals("Legacy V1 Pattern", loaded!!.name)
    assertEquals(2, loaded.vertices.size)
    assertEquals(1, loaded.edges.size)
    assertTrue(loaded.circles.isEmpty())
    assertTrue(loaded.arcs.isEmpty())
  }

  @Test
  fun `extend and trim edge operate against intersections and paper bounds`() {
    var doc = emptyDoc(200.0, 200.0)
    // Short horizontal edge from (50, 100) to (120, 100)
    doc = TransformEngine.addSegmentWithDeduplication(doc, Vec2(50.0, 100.0), Vec2(120.0, 100.0), FoldAssignment.MOUNTAIN)
    // Vertical blocker at x = 150
    doc = TransformEngine.addSegmentWithDeduplication(doc, Vec2(150.0, 0.0), Vec2(150.0, 200.0), FoldAssignment.VALLEY)

    val horizEdgeId = doc.edges.first { it.assignment == FoldAssignment.MOUNTAIN }.id
    val extended = CreaseEditingEngine.extendEdgeTowardTap(doc, horizEdgeId, Vec2(120.0, 100.0))
    val extendedEdge = extended.edges.first { it.id == horizEdgeId }
    val endPos = extended.vertexById(extendedEdge.endVertexId)!!.position
    assertEquals(150.0, endPos.x, 1e-4)

    // Now split/trim left of blocker: add another vertical at x = 100 and trim between 100 and 150
    var trimDoc = TransformEngine.addSegmentWithDeduplication(extended, Vec2(100.0, 0.0), Vec2(100.0, 200.0), FoldAssignment.VALLEY)
    trimDoc = CreaseEditingEngine.trimEdgeAtTap(trimDoc, horizEdgeId, Vec2(125.0, 100.0))
    val remainingMountains = trimDoc.edges.filter { it.assignment == FoldAssignment.MOUNTAIN }
    assertEquals(1, remainingMountains.size)
    val startX = trimDoc.vertexById(remainingMountains[0].startVertexId)!!.position.x
    val endX = trimDoc.vertexById(remainingMountains[0].endVertexId)!!.position.x
    assertEquals(50.0, kotlin.math.min(startX, endX), 1e-4)
    assertEquals(100.0, kotlin.math.max(startX, endX), 1e-4)
  }
}
