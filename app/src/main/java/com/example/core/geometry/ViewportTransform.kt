package com.example.core.geometry

import com.example.core.model.PaperSpec
import com.example.core.model.Vec2
import kotlin.math.min

data class ViewportTransform(
  val canvasWidthPx: Float,
  val canvasHeightPx: Float,
  val zoom: Float,
  val panOffsetX: Float,
  val panOffsetY: Float,
  val paper: PaperSpec,
) {
  private val fitPaddingFactor = 0.80f

  val basePixelsPerMm: Float
    get() {
      if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return 1f
      val scaleX = (canvasWidthPx * fitPaddingFactor) / paper.widthMm.toFloat().coerceAtLeast(1f)
      val scaleY = (canvasHeightPx * fitPaddingFactor) / paper.heightMm.toFloat().coerceAtLeast(1f)
      return min(scaleX, scaleY)
    }

  val pixelsPerMm: Float
    get() = basePixelsPerMm * zoom

  fun paperToScreen(point: Vec2): Pair<Float, Float> {
    val scale = pixelsPerMm
    val cx = canvasWidthPx / 2f + panOffsetX
    val cy = canvasHeightPx / 2f + panOffsetY
    val sx = cx + ((point.x - paper.widthMm / 2.0) * scale).toFloat()
    val sy = cy + ((point.y - paper.heightMm / 2.0) * scale).toFloat()
    return sx to sy
  }

  fun screenToPaper(screenX: Float, screenY: Float): Vec2 {
    val scale = pixelsPerMm.toDouble().coerceAtLeast(1e-6)
    val cx = canvasWidthPx / 2.0 + panOffsetX
    val cy = canvasHeightPx / 2.0 + panOffsetY
    val px = ((screenX - cx) / scale) + paper.widthMm / 2.0
    val py = ((screenY - cy) / scale) + paper.heightMm / 2.0
    return Vec2(px, py)
  }
}
