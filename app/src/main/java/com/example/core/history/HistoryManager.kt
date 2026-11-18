package com.example.core.history

import com.example.core.model.CreasePatternDocument
import com.example.core.model.Edge
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import com.example.core.model.Vertex

interface EditorCommand {
  val description: String

  fun execute(doc: CreasePatternDocument): CreasePatternDocument

  fun undo(doc: CreasePatternDocument): CreasePatternDocument
}

class AddVertexCommand(private val vertex: Vertex) : EditorCommand {
  override val description: String = "Add vertex"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    if (doc.vertices.any { it.id == vertex.id }) return doc
    return doc.copy(vertices = doc.vertices + vertex, updatedAt = System.currentTimeMillis())
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      vertices = doc.vertices.filterNot { it.id == vertex.id },
      updatedAt = System.currentTimeMillis(),
    )
  }
}

class AddCreaseCommand(
  private val createdStartVertex: Vertex?,
  private val createdEndVertex: Vertex?,
  private val edge: Edge,
) : EditorCommand {
  override val description: String = "Add ${edge.assignment.label} crease"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    val newVertices = buildList {
      addAll(doc.vertices)
      if (createdStartVertex != null && none { it.id == createdStartVertex.id }) {
        add(createdStartVertex)
      }
      if (createdEndVertex != null && none { it.id == createdEndVertex.id }) {
        add(createdEndVertex)
      }
    }
    val newEdges =
      if (doc.edges.any { it.id == edge.id }) doc.edges else doc.edges + edge
    return doc.copy(
      vertices = newVertices,
      edges = newEdges,
      updatedAt = System.currentTimeMillis(),
    )
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    val removedVertexIds =
      setOfNotNull(createdStartVertex?.id, createdEndVertex?.id)
    return doc.copy(
      vertices = doc.vertices.filterNot { it.id in removedVertexIds },
      edges = doc.edges.filterNot { it.id == edge.id },
      updatedAt = System.currentTimeMillis(),
    )
  }
}

class DeleteEdgeCommand(private val edge: Edge) : EditorCommand {
  override val description: String = "Delete crease"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      edges = doc.edges.filterNot { it.id == edge.id },
      updatedAt = System.currentTimeMillis(),
    )
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    if (doc.edges.any { it.id == edge.id }) return doc
    return doc.copy(edges = doc.edges + edge, updatedAt = System.currentTimeMillis())
  }
}

class DeleteVertexCommand(
  private val vertex: Vertex,
  private val removedEdges: List<Edge>,
) : EditorCommand {
  override val description: String = "Delete vertex"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    val removedEdgeIds = removedEdges.map { it.id }.toSet()
    return doc.copy(
      vertices = doc.vertices.filterNot { it.id == vertex.id },
      edges = doc.edges.filterNot { it.id in removedEdgeIds },
      updatedAt = System.currentTimeMillis(),
    )
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    val restoredVertices =
      if (doc.vertices.any { it.id == vertex.id }) doc.vertices else doc.vertices + vertex
    val existingEdgeIds = doc.edges.map { it.id }.toSet()
    val restoredEdges = doc.edges + removedEdges.filterNot { it.id in existingEdgeIds }
    return doc.copy(
      vertices = restoredVertices,
      edges = restoredEdges,
      updatedAt = System.currentTimeMillis(),
    )
  }
}

class ChangeEdgeAssignmentCommand(
  private val edgeId: String,
  private val oldAssignment: FoldAssignment,
  private val newAssignment: FoldAssignment,
) : EditorCommand {
  override val description: String = "Assign ${newAssignment.label}"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      edges =
        doc.edges.map { edge ->
          if (edge.id == edgeId) edge.copy(assignment = newAssignment) else edge
        },
      updatedAt = System.currentTimeMillis(),
    )
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      edges =
        doc.edges.map { edge ->
          if (edge.id == edgeId) edge.copy(assignment = oldAssignment) else edge
        },
      updatedAt = System.currentTimeMillis(),
    )
  }
}

class MoveVertexCommand(
  private val vertexId: String,
  private val oldPosition: Vec2,
  private val newPosition: Vec2,
) : EditorCommand {
  override val description: String = "Move vertex"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      vertices =
        doc.vertices.map { v ->
          if (v.id == vertexId) v.copy(position = newPosition) else v
        },
      updatedAt = System.currentTimeMillis(),
    )
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(
      vertices =
        doc.vertices.map { v ->
          if (v.id == vertexId) v.copy(position = oldPosition) else v
        },
      updatedAt = System.currentTimeMillis(),
    )
  }
}

class UpdatePaperSpecCommand(
  private val oldPaper: PaperSpec,
  private val newPaper: PaperSpec,
) : EditorCommand {
  override val description: String = "Update paper settings"

  override fun execute(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(paper = newPaper, updatedAt = System.currentTimeMillis())
  }

  override fun undo(doc: CreasePatternDocument): CreasePatternDocument {
    return doc.copy(paper = oldPaper, updatedAt = System.currentTimeMillis())
  }
}

class HistoryManager(private val maxHistorySize: Int = 200) {
  private val undoStack = ArrayDeque<EditorCommand>()
  private val redoStack = ArrayDeque<EditorCommand>()

  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  fun execute(document: CreasePatternDocument, command: EditorCommand): CreasePatternDocument {
    val updated = command.execute(document)
    undoStack.addLast(command)
    if (undoStack.size > maxHistorySize) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    return updated
  }

  fun undo(document: CreasePatternDocument): CreasePatternDocument {
    val command = undoStack.removeLastOrNull() ?: return document
    redoStack.addLast(command)
    return command.undo(document)
  }

  fun redo(document: CreasePatternDocument): CreasePatternDocument {
    val command = redoStack.removeLastOrNull() ?: return document
    undoStack.addLast(command)
    return command.execute(document)
  }

  fun clear() {
    undoStack.clear()
    redoStack.clear()
  }
}
