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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.docuview.app.data.AnnotationColor
import kr.docuview.app.data.AnnotationStroke
import kr.docuview.app.data.AnnotationTool
import kr.docuview.app.data.BookmarkEntity
import kr.docuview.app.data.DocumentDao
import kr.docuview.app.data.DocumentEntity
import kr.docuview.app.data.PageAnnotationEntity
import kr.docuview.app.data.decodePoints
import kr.docuview.app.data.encodeAnnotationsForWebView
import kr.docuview.app.data.encodePoints
import kr.docuview.app.reader.AnnotationRenderer
import kr.docuview.app.reader.HwpReaderView
import kr.docuview.app.reader.PageShareHelper
import kr.docuview.app.reader.PdfReaderView
import kr.docuview.app.reader.PdfSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val startPage = if (restoreLastPage) document.lastPage.coerceAtLeast(0) else 0
    var currentPage by remember(document.id) { mutableIntStateOf(startPage) }
    var requestedPage by remember(document.id) { mutableIntStateOf(startPage) }
    var pageCount by remember(document.id) { mutableIntStateOf(document.totalPages.coerceAtLeast(0)) }

    var annotationMode by remember(document.id) { mutableStateOf(false) }
    var annotationTool by remember(document.id) { mutableStateOf(AnnotationTool.NONE) }
    var annotationColor by remember(document.id) { mutableStateOf(AnnotationColor.RED) }
    var strokes by remember(document.id) { mutableStateOf<List<AnnotationStroke>>(emptyList()) }
    var pdfSession by remember(document.id) { mutableStateOf<PdfSession?>(null) }
    var sharing by remember(document.id) { mutableStateOf(false) }
    var hwpUndoRequest by remember(document.id) { mutableIntStateOf(0) }
    var hwpClearRequest by remember(document.id) { mutableIntStateOf(0) }
    var hwpShareRequest by remember(document.id) { mutableIntStateOf(0) }

    val annotationsByPage = remember(strokes) { strokes.groupBy { it.pageIndex } }
    val isPdf = document.extension == "pdf"

    LaunchedEffect(document.id) {
        dao.observeAnnotations(document.id).collect { entities ->
            strokes = entities.map { entity ->
                AnnotationStroke(
                    id = entity.strokeId,
                    pageIndex = entity.pageIndex,
                    tool = runCatching { AnnotationTool.valueOf(entity.tool) }.getOrDefault(AnnotationTool.PEN),
                    color = runCatching { AnnotationColor.valueOf(entity.color) }.getOrDefault(AnnotationColor.RED),
                    points = decodePoints(entity.points),
                )
            }
        }
    }

    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            val clamped = currentPage.coerceIn(0, pageCount - 1)
            if (clamped != currentPage) {
                currentPage = clamped
                requestedPage = clamped
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

    fun saveStroke(stroke: AnnotationStroke) {
        scope.launch {
            dao.insertAnnotation(
                PageAnnotationEntity(
                    documentId = document.id,
                    strokeId = stroke.id,
                    pageIndex = stroke.pageIndex,
                    tool = stroke.tool.name,
                    color = stroke.color.name,
                    points = encodePoints(stroke.points),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    val topBarPaddingVertical = if (compactMode) 4.dp else 6.dp
    val bottomBarPaddingVertical = if (compactMode) 2.dp else 4.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
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
                    if (isPdf || document.extension == "hwp" || document.extension == "hwpx") {
                        IconButton(onClick = { annotationMode = !annotationMode }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = if (annotationMode) "주석 닫기" else "주석 열기",
                                tint = if (annotationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            enabled = !sharing,
                            onClick = {
                                if (!isPdf) {
                                    // HWP: WebView renders the annotated page and sends PNG bytes back.
                                    hwpShareRequest++
                                    return@IconButton
                                }
                                scope.launch {
                                    sharing = true
                                    try {
                                        val session = pdfSession
                                            ?: throw IllegalStateException("PDF를 아직 불러오지 못했습니다.")
                                        val safePage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                        val base = withContext(Dispatchers.IO) {
                                            session.renderPage(safePage, 1600)
                                        }
                                        val shared = AnnotationRenderer.renderSharedPage(
                                            base,
                                            strokes.filter { it.pageIndex == currentPage },
                                        )
                                        PageShareHelper.shareBitmap(
                                            context,
                                            shared,
                                            "docuview-page-${currentPage + 1}-${System.currentTimeMillis()}.png",
                                        )
                                    } finally {
                                        sharing = false
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "주석 페이지 공유")
                        }
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
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (isPdf) {
                PdfReaderView(
                    filePath = document.localPath,
                    initialPage = startPage,
                    requestedPage = currentPage,
                    onPageChanged = { page -> currentPage = page },
                    onPageCount = { count -> pageCount = count },
                    modifier = Modifier.fillMaxSize(),
                    annotationsByPage = annotationsByPage,
                    annotationTool = annotationTool,
                    annotationColor = annotationColor,
                    annotationEnabled = annotationMode,
                    onStrokeComplete = ::saveStroke,
                    sessionRef = { pdfSession = it },
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
                    annotationEnabled = annotationMode,
                    annotationTool = annotationTool.name,
                    annotationColorHex = annotationColor.hex,
                    annotationsJson = remember(strokes) { encodeAnnotationsForWebView(strokes) },
                    onStroke = { pageIndex, tool, colorHex, pointsJson ->
                        val toolValue = runCatching { AnnotationTool.valueOf(tool) }.getOrDefault(AnnotationTool.PEN)
                        val colorValue = AnnotationColor.entries.firstOrNull { it.hex.equals(colorHex, ignoreCase = true) }
                            ?: AnnotationColor.RED
                        // Parse JSON like [[0.1,0.2],[0.3,0.4]] produced by the WebView.
                        val points = Regex("\\[([0-9.\\-eE]+),([0-9.\\-eE]+)]")
                            .findAll(pointsJson)
                            .mapNotNull { match ->
                                val x = match.groupValues[1].toFloatOrNull()
                                val y = match.groupValues[2].toFloatOrNull()
                                if (x != null && y != null) x to y else null
                            }
                            .toList()
                        saveStroke(
                            AnnotationStroke.create(
                                pageIndex = pageIndex,
                                tool = toolValue,
                                color = colorValue,
                                points = points,
                            ),
                        )
                    },
                    currentPageForUndo = currentPage,
                    undoRequest = hwpUndoRequest,
                    clearRequest = hwpClearRequest,
                    shareRequest = hwpShareRequest,
                    onSharedImage = { bytes ->
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            PageShareHelper.shareBitmap(
                                context,
                                bitmap,
                                "docuview-hwp-page-${currentPage + 1}-${System.currentTimeMillis()}.png",
                            )
                        }
                    },
                )
            }

            if (annotationMode) {
                AnnotationToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    tool = annotationTool,
                    color = annotationColor,
                    onToolChange = { annotationTool = it },
                    onColorChange = { annotationColor = it },
                    onUndo = {
                        if (isPdf) {
                            val last = strokes.filter { it.pageIndex == currentPage }.maxByOrNull { it.id }
                            if (last != null) {
                                scope.launch { dao.deleteAnnotation(document.id, last.id) }
                            }
                        } else {
                            hwpUndoRequest++
                            val last = strokes.filter { it.pageIndex == currentPage }.maxByOrNull { it.id }
                            if (last != null) {
                                scope.launch { dao.deleteAnnotation(document.id, last.id) }
                            }
                        }
                    },
                    onClearAll = {
                        scope.launch { dao.clearPageAnnotations(document.id, currentPage) }
                        if (!isPdf) hwpClearRequest++
                    },
                    onClose = { annotationMode = false },
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
