package kr.docuview.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    restoreLastPage: Boolean = true,
    onRestoreLastPageChange: (Boolean) -> Unit = {},
    showBookmarks: Boolean = true,
    onShowBookmarksChange: (Boolean) -> Unit = {},
    compactMode: Boolean = false,
    onCompactModeChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = true,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    themeMode: String = "SYSTEM",
    onThemeModeChange: (String) -> Unit = {},
    onClearRecentDocuments: () -> Unit = {},
) {
    val context = LocalContext.current
    var restoreLastPageState by remember(restoreLastPage) { mutableStateOf(restoreLastPage) }
    var showBookmarksState by remember(showBookmarks) { mutableStateOf(showBookmarks) }
    var compactModeState by remember(compactMode) { mutableStateOf(compactMode) }
    var keepScreenOnState by remember(keepScreenOn) { mutableStateOf(keepScreenOn) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableLongStateOf(calculateCacheSize(context)) }

    val themeLabel = when (themeMode) {
        "LIGHT" -> "라이트 모드"
        "DARK" -> "다크 모드"
        else -> "시스템 설정 따르기"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                TopAppBar(
                    title = {
                        Text(
                            text = "설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = CircleShape),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 읽기 설정 카드
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = "읽기 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    SettingRow(
                        title = "마지막 페이지 복원",
                        subtitle = "마지막으로 읽던 페이지를 다시 열기",
                        icon = Icons.Default.Visibility,
                        trailing = {
                            Switch(
                                checked = restoreLastPageState,
                                onCheckedChange = {
                                    restoreLastPageState = it
                                    onRestoreLastPageChange(it)
                                },
                            )
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    SettingRow(
                        title = "북마크 표시",
                        subtitle = "리더 상단에서 북마크 상태를 표시",
                        icon = Icons.Outlined.BookmarkBorder,
                        trailing = {
                            Switch(
                                checked = showBookmarksState,
                                onCheckedChange = {
                                    showBookmarksState = it
                                    onShowBookmarksChange(it)
                                },
                            )
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    SettingRow(
                        title = "컴팩트 뷰",
                        subtitle = "툴바와 여백을 더 좁게 관리",
                        icon = Icons.Default.Storage,
                        trailing = {
                            Switch(
                                checked = compactModeState,
                                onCheckedChange = {
                                    compactModeState = it
                                    onCompactModeChange(it)
                                },
                            )
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    SettingRow(
                        title = "화면 켜짐 유지",
                        subtitle = "문서를 읽는 동안 화면이 꺼지지 않음",
                        icon = Icons.Default.Lightbulb,
                        trailing = {
                            Switch(
                                checked = keepScreenOnState,
                                onCheckedChange = {
                                    keepScreenOnState = it
                                    onKeepScreenOnChange(it)
                                },
                            )
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    SettingActionRow(
                        title = "화면 테마",
                        subtitle = themeLabel,
                        icon = Icons.Default.DarkMode,
                        onClick = { showThemeDialog = true },
                    )
                }
            }

            // 관리 카드 (용량 확인 및 캐시 비우기 기능 포함)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "관리",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    SettingActionRow(
                        title = "최근 문서 정리",
                        subtitle = "최근 열람 기록을 정리합니다",
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = { showClearConfirmDialog = true },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // 임시 캐시 용량 확인 및 캐시 비우기
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "임시 캐시 용량",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatSize(cacheSize),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = {
                                clearAppCache(context)
                                cacheSize = calculateCacheSize(context)
                                Toast.makeText(context, "임시 캐시가 비워졌습니다.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("캐시 비우기", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "※ 문서 열기 속도가 느리면 캐시 비우기를 눌러주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 앱 정보 카드
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(text = "DocuView", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Android PDF · HWP 문서 뷰어",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("버전", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1.0.0")
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("모드", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("오프라인 읽기")
                    }
                }
            }
        }
    }

    // 테마 변경 대화상자
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("화면 테마 선택") },
            text = {
                Column {
                    val options = listOf(
                        "SYSTEM" to "시스템 설정 따르기",
                        "LIGHT" to "라이트 모드",
                        "DARK" to "다크 모드",
                    )
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onThemeModeChange(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (themeMode == key),
                                onClick = {
                                    onThemeModeChange(key)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    // 최근 문서 정리 확인 대화상자
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("최근 문서 정리") },
            text = { Text("최근 열람 기록을 모두 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRecentDocuments()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "최근 열람 기록이 정리되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("정리하기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("취소")
                }
            },
        )
    }
}

private fun calculateCacheSize(context: Context): Long {
    var size = 0L
    try {
        context.cacheDir?.walkTopDown()?.forEach { file ->
            if (file.isFile) size += file.length()
        }
    } catch (_: Exception) { }
    return size
}

private fun clearAppCache(context: Context) {
    try {
        context.cacheDir?.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
    } catch (_: Exception) { }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0.0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(java.util.Locale.getDefault(), "%.2f MB", mb)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        trailing()
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
