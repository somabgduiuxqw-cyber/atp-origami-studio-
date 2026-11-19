package com.example.core.spatial

import com.example.core.model.Edge
import com.example.core.model.Vec2
import com.example.core.model.Vertex
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Uniform 2D spatial hash grid in paper-space coordinates for O(1) localized queries
 * on large crease patterns (nearest vertices, candidate intersecting edges).
 */
class SpatialGridIndex(
  private val cellSizeMm: Double = 25.0,
) {
  private val vertexCells = HashMap<Long, MutableList<Vertex>>()
  private val edgeCells = HashMap<Long, MutableList<Edge>>()

  fun index(vertices: List<Vertex>, edges: List<Edge>) {
    vertexCells.clear()
    edgeCells.clear()
    val vertexMap = HashMap<String, Vertex>(vertices.size * 2)
    for (v in vertices) {
      vertexMap[v.id] = v
      val key = packCell(cellCoord(v.position.x), cellCoord(v.position.y))
      vertexCells.getOrPut(key) { mutableListOf() }.add(v)
    }

    for (edge in edges) {
      val a = vertexMap[edge.startVertexId]?.position ?: continue
      val b = vertexMap[edge.endVertexId]?.position ?: continue
      val minCx = cellCoord(min(a.x, b.x))
      val maxCx = cellCoord(max(a.x, b.x))
      val minCy = cellCoord(min(a.y, b.y))
      val maxCy = cellCoord(max(a.y, b.y))
      for (cx in minCx..maxCx) {
        for (cy in minCy..maxCy) {
          val key = packCell(cx, cy)
          edgeCells.getOrPut(key) { mutableListOf() }.add(edge)
        }
      }
    }
  }

  fun queryVerticesNear(point: Vec2, radiusMm: Double): List<Vertex> {
    val minCx = cellCoord(point.x - radiusMm)
    val maxCx = cellCoord(point.x + radiusMm)
    val minCy = cellCoord(point.y - radiusMm)
    val maxCy = cellCoord(point.y + radiusMm)
    val result = mutableListOf<Vertex>()
    for (cx in minCx..maxCx) {
      for (cy in minCy..maxCy) {
        vertexCells[packCell(cx, cy)]?.let { result.addAll(it) }
      }
    }
    return result
  }

  fun queryEdgesNear(point: Vec2, radiusMm: Double): Set<Edge> {
    val minCx = cellCoord(point.x - radiusMm)
    val maxCx = cellCoord(point.x + radiusMm)
    val minCy = cellCoord(point.y - radiusMm)
    val maxCy = cellCoord(point.y + radiusMm)
    val result = LinkedHashSet<Edge>()
    for (cx in minCx..maxCx) {
      for (cy in minCy..maxCy) {
        edgeCells[packCell(cx, cy)]?.let { result.addAll(it) }
      }
    }
    return result
  }

  fun queryEdgesInBox(minPt: Vec2, maxPt: Vec2): Set<Edge> {
    val minCx = cellCoord(minPt.x)
    val maxCx = cellCoord(maxPt.x)
    val minCy = cellCoord(minPt.y)
    val maxCy = cellCoord(maxPt.y)
    val result = LinkedHashSet<Edge>()
    for (cx in minCx..maxCx) {
      for (cy in minCy..maxCy) {
        edgeCells[packCell(cx, cy)]?.let { result.addAll(it) }
      }
    }
    return result
  }

  private fun cellCoord(valueMm: Double): Int = floor(valueMm / cellSizeMm).toInt()

  private fun packCell(cx: Int, cy: Int): Long {
    return (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
  }
}
