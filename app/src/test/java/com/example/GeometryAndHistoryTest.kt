package com.example

import com.example.core.geometry.GeometryEngine
import com.example.core.geometry.ViewportTransform
import com.example.core.history.AddCreaseCommand
import com.example.core.history.ChangeEdgeAssignmentCommand
import com.example.core.history.DeleteVertexCommand
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
import com.example.data.model.OrigamiTemplateId
import com.example.data.model.TemplateCatalog
import com.example.data.serializers.ProjectJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryAndHistoryTest {

  @Test
  fun `segment intersection detects crossing diagonals`() {
    val hit =
      GeometryEngine.segmentIntersection(
        Vec2(0.0, 0.0),
        Vec2(200.0, 200.0),
        Vec2(0.0, 200.0),
        Vec2(200.0, 0.0),
      )
    assertNotNull(hit)
    assertEquals(100.0, hit!!.x, 1e-6)
    assertEquals(100.0, hit.y, 1e-6)
  }

  @Test
  fun `viewport transform round-trips screen and paper coordinates`() {
    val transform =
      ViewportTransform(
        canvasWidthPx = 1080f,
        canvasHeightPx = 1920f,
        zoom = 1.75f,
        panOffsetX = 42f,
        panOffsetY = -18f,
        paper = PaperSpec(200.0, 200.0, 25.0),
      )
    val original = Vec2(137.5, 62.5)
    val (sx, sy) = transform.paperToScreen(original)
    val roundTrip = transform.screenToPaper(sx, sy)
    assertEquals(original.x, roundTrip.x, 1e-3)
    assertEquals(original.y, roundTrip.y, 1e-3)
  }

  @Test
  fun `snap engine prioritizes vertex over grid intersection`() {
    val v1 = Vertex("v1", Vec2(52.0, 52.0))
    val doc =
      CreasePatternDocument(
        id = "p1",
        name = "Test",
        paper = PaperSpec(200.0, 200.0, 25.0),
        vertices = listOf(v1),
        edges = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
      )
    val snap =
      SnapEngine.resolveSnap(
        rawPaperPoint = Vec2(50.5, 50.5),
        document = doc,
        config = SnapConfig(snapToGrid = true, snapToVertex = true, gridSpacingMm = 25.0, vertexSnapRadiusMm = 8.0, gridSnapRadiusMm = 6.0),
      )
    assertEquals(SnapKind.VERTEX, snap.kind)
    assertEquals("v1", snap.snappedVertexId)
    assertEquals(52.0, snap.position.x, 1e-6)
  }

  @Test
  fun `history manager executes undoes and redoes crease and vertex commands`() {
    val baseDoc =
      TemplateCatalog.buildDocument(
        templateId = OrigamiTemplateId.BLANK,
        projectId = "doc1",
        projectName = "Blank",
        paper = PaperSpec(200.0, 200.0, 25.0),
        now = 100L,
      )
    val initialVertexCount = baseDoc.vertices.size
    val initialEdgeCount = baseDoc.edges.size

    val history = HistoryManager()
    val startV = Vertex("s1", Vec2(100.0, 0.0))
    val endV = Vertex("e1", Vec2(100.0, 200.0))
    val crease = Edge("edge1", startV.id, endV.id, FoldAssignment.MOUNTAIN)

    val afterAdd = history.execute(baseDoc, AddCreaseCommand(startV, endV, crease))
    assertEquals(initialVertexCount + 2, afterAdd.vertices.size)
    assertEquals(initialEdgeCount + 1, afterAdd.edges.size)
    assertTrue(history.canUndo)

    val afterFlip =
      history.execute(
        afterAdd,
        ChangeEdgeAssignmentCommand(crease.id, FoldAssignment.MOUNTAIN, FoldAssignment.VALLEY),
      )
    assertEquals(FoldAssignment.VALLEY, afterFlip.edges.first { it.id == crease.id }.assignment)

    val afterUndoFlip = history.undo(afterFlip)
    assertEquals(FoldAssignment.MOUNTAIN, afterUndoFlip.edges.first { it.id == crease.id }.assignment)

    val afterUndoAdd = history.undo(afterUndoFlip)
    assertEquals(initialVertexCount, afterUndoAdd.vertices.size)
    assertEquals(initialEdgeCount, afterUndoAdd.edges.size)
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)

    val afterRedoAdd = history.redo(afterUndoAdd)
    assertEquals(initialEdgeCount + 1, afterRedoAdd.edges.size)

    // Delete vertex cascades connected edge and restores cleanly on undo
    val afterDeleteV = history.execute(afterRedoAdd, DeleteVertexCommand(startV, listOf(crease)))
    assertEquals(initialVertexCount + 1, afterDeleteV.vertices.size)
    assertEquals(initialEdgeCount, afterDeleteV.edges.size)

    val restored = history.undo(afterDeleteV)
    assertEquals(initialVertexCount + 2, restored.vertices.size)
    assertEquals(initialEdgeCount + 1, restored.edges.size)
  }

  @Test
  fun `json serializer round-trips bird base document`() {
    val bird =
      TemplateCatalog.buildDocument(
        templateId = OrigamiTemplateId.BIRD_BASE,
        projectId = "bird-1",
        projectName = "Bird Base",
        paper = PaperSpec(200.0, 200.0, 25.0),
        now = 500L,
      )
    val serializer = ProjectJsonSerializer()
    val json = serializer.serialize(bird)
    val restored = serializer.deserialize(json)
    assertNotNull(restored)
    assertEquals(bird.name, restored!!.name)
    assertEquals(bird.vertices.size, restored.vertices.size)
    assertEquals(bird.edges.size, restored.edges.size)
  }
}
