package kr.docuview.app.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kr.docuview.app.data.AnnotationStroke
import kr.docuview.app.data.AnnotationTool

object AnnotationRenderer {
    fun renderSharedPage(base: Bitmap, strokes: List<AnnotationStroke>): Bitmap {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()
        strokes.forEach { stroke ->
            val points = stroke.points.map { it.first * w to it.second * h }
            when (stroke.tool) {
                AnnotationTool.PEN -> {
                    if (points.isEmpty()) return@forEach
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stroke.color.argb
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = 8f
                    }
                    if (points.size == 1) {
                        canvas.drawCircle(points[0].first, points[0].second, 4f, paint)
                    } else {
                        for (i in 1 until points.size) {
                            canvas.drawLine(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second, paint)
                        }
                    }
                }
                AnnotationTool.HIGHLIGHT -> {
                    if (points.isEmpty()) return@forEach
                    // Draw the whole stroke as ONE path on an offscreen layer so overlapping
                    // segments do not stack alpha and become opaque.
                    val layer = Bitmap.createBitmap(output.width, output.height, Bitmap.Config.ARGB_8888)
                    val layerCanvas = Canvas(layer)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stroke.color.argb
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = 40f
                    }
                    val path = Path()
                    path.moveTo(points[0].first, points[0].second)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].first, points[i].second)
                    }
                    layerCanvas.drawPath(path, paint)

                    val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = (0.35f * 255).toInt()
                    }
                    canvas.drawBitmap(layer, 0f, 0f, alphaPaint)
                    layer.recycle()
                }
                AnnotationTool.NONE -> Unit
            }
        }
        return output
    }
}
