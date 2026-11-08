package kr.docuview.app.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.docuview.app.data.BookmarkEntity
import kr.docuview.app.data.DocumentDao
import kr.docuview.app.data.DocumentEntity
import kr.docuview.app.reader.HwpReaderView
import kr.docuview.app.reader.PdfReaderView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    document: DocumentEntity,
    dao: DocumentDao,
    onBack: () -> Unit,
    restoreLastPage: Boolean = true,
    compactMode: Boolean = false,
    keepScreenOn: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarks by dao.observeBookmarks(document.id).collectAsState(initial = emptyList())

    val startPage = if (restoreLastPage) document.lastPage.coerceAtLeast(0) else 0
    var currentPage by remember(document.id) { mutableIntStateOf(startPage) }
    var requestedPage by remember(document.id) { mutableIntStateOf(startPage) }
    var pageCount by remember(document.id) { mutableIntStateOf(document.totalPages.coerceAtLeast(0)) }
    var pendingPageRequest by remember(document.id) { mutableStateOf<Int?>(null) }


    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            val clamped = currentPage.coerceIn(0, pageCount - 1)
            if (clamped != currentPage) {
                currentPage = clamped
                requestedPage = clamped
                pendingPageRequest = null
            }
        }
    }

    BackHandler(onBack = onBack)

    DisposableEffect(keepScreenOn) {
        val activity = context as? android.app.Activity
        if (keepScreenOn && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(document.id) {
        dao.touchDocument(document.id, System.currentTimeMillis())
    }

    LaunchedEffect(document.id, currentPage, pageCount) {
        if (pageCount > 0) {
            delay(350)
            dao.updateProgress(
                id = document.id,
                pageIndex = currentPage.coerceIn(0, pageCount - 1),
                pageCount = pageCount,
                openedAt = System.currentTimeMillis(),
            )
        }
    }

    val topBarPaddingVertical = if (compactMode) 4.dp else 6.dp
    val bottomBarPaddingVertical = if (compactMode) 2.dp else 4.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = topBarPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = CircleShape),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            document.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (compactMode) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (pageCount > 0) "${currentPage + 1} / $pageCount" else document.extension.uppercase(),
                            style = if (compactMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }


                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = bottomBarPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        enabled = pageCount > 0 && currentPage > 0,
                        onClick = {
                            val target = (currentPage - 1).coerceAtLeast(0)
                            currentPage = target
                            requestedPage = target
                        },
                        modifier = Modifier
                            .size(if (compactMode) 26.dp else 30.dp)
                            .background(
                                if (pageCount > 0 && currentPage > 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "이전 페이지",
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier
                            .height(if (compactMode) 24.dp else 26.dp)
                            .defaultMinSize(minWidth = 68.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (pageCount > 0) "${currentPage + 1} / $pageCount" else "페이지 확인 중",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                lineHeight = 8.sp,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        enabled = pageCount > 0 && currentPage < pageCount - 1,
                        onClick = {
                            val target = (currentPage + 1).coerceAtMost(pageCount - 1)
                            currentPage = target
                            requestedPage = target
                        },
                        modifier = Modifier
                            .size(if (compactMode) 26.dp else 30.dp)
                            .background(
                                if (pageCount > 0 && currentPage < pageCount - 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "다음 페이지",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        Box(
            modifier = contentModifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (document.extension == "pdf") {
                PdfReaderView(
                    filePath = document.localPath,
                    initialPage = startPage,
                    requestedPage = currentPage,
                    onPageChanged = { page ->
                        currentPage = page
                    },
                    onPageCount = { count -> pageCount = count },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HwpReaderView(
                    filePath = document.localPath,
                    documentId = document.id,
                    initialPage = startPage,
                    requestedPage = currentPage,
                    onPageChanged = { page, count ->
                        currentPage = page
                        if (count > 0) pageCount = count
                    },
                    onPageCount = { count -> pageCount = count },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }


}

@Composable
private fun BookmarkDialog(
    bookmarks: List<BookmarkEntity>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("북마크") },
        text = {
            if (bookmarks.isEmpty()) {
                Text("저장된 북마크가 없습니다.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(bookmarks, key = { it.pageIndex }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(bookmark.pageIndex) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("${bookmark.pageIndex + 1} 페이지", modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { onDelete(bookmark.pageIndex) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "북마크 삭제")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}