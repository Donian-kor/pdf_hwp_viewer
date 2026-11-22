package kr.docuview.app.data

import java.util.UUID

// PEN: user draws freely anywhere. HIGHLIGHT: translucent marker over text.
enum class AnnotationTool { NONE, PEN, HIGHLIGHT }

/** Five rainbow colors. Order is fixed: red, orange, yellow, green, blue. */
enum class AnnotationColor(val label: String, val hex: String, val argb: Int) {
    RED("빨강", "#FF3B30", 0xFFFF3B30.toInt()),
    ORANGE("주황", "#FF9500", 0xFFFF9500.toInt()),
    YELLOW("노랑", "#FFCC00", 0xFFFFCC00.toInt()),
    GREEN("초록", "#34C759", 0xFF34C759.toInt()),
    BLUE("파랑", "#007AFF", 0xFF007AFF.toInt()),
}

/** A single user-drawn mark. Points are normalized to the page (0f..1f). */
data class AnnotationStroke(
    val id: String,
    val pageIndex: Int,
    val tool: AnnotationTool,
    val color: AnnotationColor,
    val points: List<Pair<Float, Float>>,
    val text: String? = null,
) {
    companion object {
        fun create(
            pageIndex: Int,
            tool: AnnotationTool,
            color: AnnotationColor,
            points: List<Pair<Float, Float>>,
            text: String? = null,
        ) = AnnotationStroke(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            tool = tool,
            color = color,
            points = points,
            text = text,
        )
    }
}

internal fun encodePoints(points: List<Pair<Float, Float>>): String =
    points.joinToString(";") { "${it.first},${it.second}" }

/** Serializes strokes to the JSON shape expected by the HWP WebView renderer. */
fun encodeAnnotationsForWebView(strokes: List<AnnotationStroke>): String {
    fun esc(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
    return buildString {
        append('[')
        strokes.forEachIndexed { index, stroke ->
            if (index > 0) append(',')
            append("{\"pageIndex\":${stroke.pageIndex},")
            append("\"tool\":\"${esc(stroke.tool.name)}\",")
            append("\"color\":\"${esc(stroke.color.hex)}\",")
            append("\"points\":[")
            stroke.points.forEachIndexed { pIndex, point ->
                if (pIndex > 0) append(',')
                append("[${point.first},${point.second}]")
            }
            append("]}")
        }
        append(']')
    }
}

internal fun decodePoints(value: String): List<Pair<Float, Float>> =
    value.split(';').mapNotNull { part ->
        val pieces = part.split(',')
        if (pieces.size == 2) {
            val x = pieces[0].toFloatOrNull()
            val y = pieces[1].toFloatOrNull()
            if (x != null && y != null) x to y else null
        } else null
    }
