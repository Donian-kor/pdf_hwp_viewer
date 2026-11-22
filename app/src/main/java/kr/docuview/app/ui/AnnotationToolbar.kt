package kr.docuview.app.ui

import android.content.res.Configuration
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.docuview.app.data.AnnotationColor
import kr.docuview.app.data.AnnotationTool

/**
 * 반응형 주석 툴바.
 * - 세로 화면: 하단에 가로로 배치하고, 폭이 좁으면 아이콘만 표시한다.
 * - 가로 화면: 화면 오른쪽에 세로로 배치해 문서 표시 영역을 가리지 않게 한다.
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
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // 가로 모드에서는 세로 띠 모양이므로 아이콘만 보여 폭을 최소화한다.
        val compact = maxWidth < 360.dp || isLandscape

        if (isLandscape) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolbarCloseButton(onClose = onClose)
                Surface(
                    modifier = Modifier.width(128.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    // 좌측 열: 기능 버튼 4개 / 우측 열: 색상 버튼 5개
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToolbarButtons(
                            vertical = true,
                            compact = compact,
                            tool = tool,
                            onToolChange = onToolChange,
                            onUndo = onUndo,
                            onClearAll = onClearAll,
                        )
                        VerticalDivider(
                            modifier = Modifier.height(172.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        ToolbarColors(
                            vertical = true,
                            compact = true,
                            color = color,
                            onColorChange = onColorChange,
                        )
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarCloseButton(onClose = onClose)
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ToolbarButtons(
                            vertical = false,
                            compact = compact,
                            tool = tool,
                            onToolChange = onToolChange,
                            onUndo = onUndo,
                            onClearAll = onClearAll,
                        )
                        ToolbarColors(
                            vertical = false,
                            compact = compact,
                            color = color,
                            onColorChange = onColorChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarCloseButton(onClose: () -> Unit) {
    IconButton(
        onClick = onClose,
        modifier = Modifier
            .size(44.dp)
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

@Composable
private fun ToolbarButtons(
    vertical: Boolean,
    compact: Boolean,
    tool: AnnotationTool,
    onToolChange: (AnnotationTool) -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
) {
    val buttons: @Composable () -> Unit = {
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
        ActionButton(
            label = "되돌리기",
            icon = Icons.Default.Restore,
            compact = compact,
            onClick = onUndo,
        )
        ActionButton(
            label = "초기화",
            icon = Icons.Default.DeleteSweep,
            compact = compact,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error,
            onClick = onClearAll,
        )
    }

    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { buttons() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { buttons() }
    }
}

@Composable
private fun ToolbarColors(
    vertical: Boolean,
    compact: Boolean,
    color: AnnotationColor,
    onColorChange: (AnnotationColor) -> Unit,
) {
    val swatches: @Composable () -> Unit = {
        AnnotationColor.entries.forEach { c ->
            val selected = color == c
            val base = if (compact) 28.dp else 30.dp
            Box(
                modifier = Modifier
                    .size(if (selected) base + 6.dp else base)
                    .clip(CircleShape)
                    .background(Color(c.argb), CircleShape)
                    .clickable { onColorChange(c) },
            )
        }
    }

    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // 색상 5개의 전체 높이가 기능 버튼 4개 열(172dp)과 같아지도록 간격을 맞춘다.
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.5.dp else 4.dp),
        ) { swatches() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { swatches() }
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
        modifier = Modifier
            .scale(scale)
            .height(40.dp),
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
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
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
        modifier = Modifier.height(40.dp),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = if (compact) {
            androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        if (compact) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
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
