package kr.docuview.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.docuview.app.data.BookmarkEntity
import kr.docuview.app.data.DocumentDao
import kr.docuview.app.data.DocumentEntity
import kr.docuview.app.reader.HwpReaderView
import kr.docuview.app.reader.PdfReaderView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: DocumentEntity,
    dao: DocumentDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bookmarks by dao.observeBookmarks(document.id).collectAsState(initial = emptyList())
    var currentPage by remember(document.id) { mutableIntStateOf(document.lastPage.coerceAtLeast(0)) }
    var requestedPage by remember(document.id) { mutableIntStateOf(document.lastPage.coerceAtLeast(0)) }
    var pageCount by remember(document.id) { mutableIntStateOf(document.totalPages) }
    var showBookmarks by remember { mutableStateOf(false) }
    val isBookmarked = bookmarks.any { it.pageIndex == currentPage }

    BackHandler(onBack = onBack)

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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    Column {
                        Text(
                            document.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (pageCount > 0) "${currentPage + 1} / $pageCount 페이지" else document.extension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val existing = dao.getBookmark(document.id, currentPage)
                                if (existing == null) {
                                    dao.insertBookmark(
                                        BookmarkEntity(
                                            documentId = document.id,
                                            pageIndex = currentPage,
                                            createdAt = System.currentTimeMillis(),
                                        ),
                                    )
                                } else {
                                    dao.deleteBookmark(document.id, currentPage)
                                }
                            }
                        },
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isBookmarked) "현재 페이지 북마크 삭제" else "현재 페이지 북마크 추가",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "북마크 목록")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        enabled = pageCount > 0 && currentPage > 0,
                        onClick = { requestedPage = (currentPage - 1).coerceAtLeast(0) },
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "이전 페이지")
                    }
                    Text(
                        if (pageCount > 0) "${currentPage + 1} / $pageCount" else "페이지 확인 중",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(
                        enabled = pageCount > 0 && currentPage < pageCount - 1,
                        onClick = { requestedPage = (currentPage + 1).coerceAtMost(pageCount - 1) },
                    ) {
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = "다음 페이지")
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        if (document.extension == "pdf") {
            PdfReaderView(
                filePath = document.localPath,
                initialPage = document.lastPage,
                requestedPage = requestedPage,
                onPageChanged = { page ->
                    currentPage = page
                    requestedPage = page
                },
                onPageCount = { count -> pageCount = count },
                modifier = contentModifier,
            )
        } else {
            HwpReaderView(
                filePath = document.localPath,
                documentId = document.id,
                initialPage = document.lastPage,
                requestedPage = requestedPage,
                onPageChanged = { page, count ->
                    currentPage = page
                    requestedPage = page
                    if (count > 0) pageCount = count
                },
                onPageCount = { count -> pageCount = count },
                modifier = contentModifier,
            )
        }
    }

    if (showBookmarks) {
        BookmarkDialog(
            bookmarks = bookmarks,
            onDismiss = { showBookmarks = false },
            onSelect = { page ->
                currentPage = page
                requestedPage = page
                showBookmarks = false
            },
            onDelete = { page ->
                scope.launch(Dispatchers.IO) { dao.deleteBookmark(document.id, page) }
            },
        )
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
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(bookmark.pageIndex) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
