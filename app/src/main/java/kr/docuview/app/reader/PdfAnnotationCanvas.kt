package kr.docuview.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kr.docuview.app.data.AnnotationColor
import kr.docuview.app.data.AnnotationStroke
import kr.docuview.app.data.AnnotationTool

@Composable
fun PdfAnnotationCanvas(
    strokes: List<AnnotationStroke>,
    tool: AnnotationTool,
    annotationColor: AnnotationColor,
    enabled: Boolean,
    onStrokeComplete: (AnnotationStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inProgress by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Always read the latest values; pointerInput lambdas can otherwise capture a stale color
    // when the user switches colors between strokes (e.g. blue -> green).
    val currentColor by rememberUpdatedState(annotationColor)
    val currentTool by rememberUpdatedState(tool)
    val currentOnStrokeComplete by rememberUpdatedState(onStrokeComplete)

    // Display-only canvas: no pointerInput, so zoom/pan gestures pass through when annotation mode is off.
    Canvas(modifier = modifier) {
        strokes.forEach { drawStroke(it) }
        val points = inProgress
        if (points.isNotEmpty()) {
            drawStroke(
                AnnotationStroke(
                    "preview",
                    -1,
                    currentTool,
                    currentColor,
                    points.map { it.x / size.width to it.y / size.height },
                ),
            )
        }
    }

    // Input canvas exists only in annotation mode; otherwise zoom/pan works exactly as before.
    if (enabled && currentTool != AnnotationTool.NONE) {
        Canvas(
            modifier = modifier
                // Key includes color so the gesture listener restarts when the color changes.
                .pointerInput(currentTool, currentColor) {
                    detectDragGestures(
                        onDragStart = { offset -> inProgress = listOf(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            inProgress += change.position
                        },
                        onDragEnd = {
                            if (inProgress.size >= 2) {
                                val points = inProgress.map { it.x / size.width to it.y / size.height }
                                currentOnStrokeComplete(
                                    AnnotationStroke.create(
                                        pageIndex = -1, // assigned by the caller
                                        tool = currentTool,
                                        color = currentColor,
                                        points = points,
                                    ),
                                )
                            }
                            inProgress = emptyList()
                        },
                        onDragCancel = { inProgress = emptyList() },
                    )
                },
        ) { }
    }
}

private fun DrawScope.drawStroke(stroke: AnnotationStroke) {
    val w = size.width
    val h = size.height
    val points = stroke.points.map { Offset(it.first * w, it.second * h) }
    val color = Color(stroke.color.argb)
    when (stroke.tool) {
        AnnotationTool.PEN -> {
            if (points.size == 1) {
                drawCircle(color, 4f, points[0])
            } else {
                for (i in 1 until points.size) {
                    drawLine(color, points[i - 1], points[i], strokeWidth = 8f)
                }
            }
        }
        AnnotationTool.HIGHLIGHT -> {
            if (points.isEmpty()) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(path = path, color = color.copy(alpha = 0.35f), style = Stroke(width = 40f))
        }
        AnnotationTool.NONE -> Unit
    }
}
