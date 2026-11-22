package kr.docuview.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.docuview.app.data.AnnotationColor
import kr.docuview.app.data.AnnotationTool

/**
 * Responsive annotation toolbar. On narrow screens the tool labels collapse to
 * icons so every action (including 초기화) stays visible and tappable.
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
    BoxWithConstraints(modifier = modifier) {
        // Below ~360dp, switch to icon-only buttons to avoid label clipping.
        val compact = maxWidth < 360.dp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "주석 툴바 닫기",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        ToolButton(
                            label = "그리기",
                            icon = Icons.Default.Edit,
                            compact = compact,
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
                            icon = Icons.Default.Highlight,
                            compact = compact,
                            selected = tool == AnnotationTool.HIGHLIGHT,
                            onClick = {
                                onToolChange(
                                    if (tool == AnnotationTool.HIGHLIGHT) AnnotationTool.NONE else AnnotationTool.HIGHLIGHT,
                                )
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        // 되돌리기
                        ActionButton(
                            label = "되돌리기",
                            icon = Icons.Default.Restore,
                            compact = compact,
                            onClick = onUndo,
                        )
                        Spacer(Modifier.width(4.dp))
                        // 초기화 - keep visible on narrow screens as icon-only.
                        ActionButton(
                            label = "초기화",
                            icon = Icons.Default.DeleteSweep,
                            compact = compact,
                            contentColor = MaterialTheme.colorScheme.error,
                            borderColor = MaterialTheme.colorScheme.error,
                            onClick = onClearAll,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnnotationColor.entries.forEach { c ->
                            val selected = color == c
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 36.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.argb), CircleShape)
                                    .clickable { onColorChange(c) },
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
    icon: ImageVector,
    compact: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "toolContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(),
        label = "toolBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(),
        label = "toolContent",
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
            contentColor = contentColor,
        ),
        contentPadding = if (compact) {
            androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        if (compact) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        } else {
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    compact: Boolean,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, borderColor),
        contentPadding = if (compact) {
            androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        if (compact) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        } else {
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}