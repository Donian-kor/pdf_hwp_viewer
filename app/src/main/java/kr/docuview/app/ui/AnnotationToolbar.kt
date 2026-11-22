package kr.docuview.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.docuview.app.data.AnnotationColor
import kr.docuview.app.data.AnnotationTool

/**
 * Annotation toolbar. The close button lives in its own transparent row ABOVE
 * the toolbar surface, so it never overlaps the action buttons.
 *
 * Tapping the currently selected tool again deselects it (NONE), which lets the
 * user pan/zoom the document while the toolbar stays open.
 */
@Composable
fun AnnotationToolbar(
    modifier: Modifier = Modifier,
    tool: AnnotationTool,
    color: AnnotationColor,
    onToolChange: (AnnotationTool) -> Unit,
    onColorChange: (AnnotationColor) -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Separate row above the toolbar: no row background, only the round button is filled.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "주석 툴바 닫기", tint = onSurface)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, outline),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Row 1: tools + actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ToolButton(
                        label = "그리기",
                        selected = tool == AnnotationTool.PEN,
                        onClick = {
                            onToolChange(
                                if (tool == AnnotationTool.PEN) AnnotationTool.NONE else AnnotationTool.PEN,
                            )
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    ToolButton(
                        label = "형광펜",
                        selected = tool == AnnotationTool.HIGHLIGHT,
                        onClick = {
                            onToolChange(
                                if (tool == AnnotationTool.HIGHLIGHT) AnnotationTool.NONE else AnnotationTool.HIGHLIGHT,
                            )
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = onUndo, border = BorderStroke(1.dp, outline)) {
                        Text(
                            "되돌리기",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = onClearAll,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(
                            "초기화",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Row 2: five colors with generous 48dp touch targets and clear spacing.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnnotationColor.entries.forEach { c ->
                        val selected = color == c
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { onColorChange(c) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 34.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.argb), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Animated container color + subtle scale for the selected state.
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "toolContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(),
        label = "toolBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(),
        label = "toolScale",
    )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}
