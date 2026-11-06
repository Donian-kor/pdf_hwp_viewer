package kr.docuview.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfReaderView(
    filePath: String,
    initialPage: Int,
    requestedPage: Int,
    onPageChanged: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var session by remember(filePath) { mutableStateOf<PdfSession?>(null) }
    var error by remember(filePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        try {
            session = withContext(Dispatchers.IO) { PdfSession(File(filePath)) }
            session?.let { onPageCount(it.pageCount) }
        } catch (exception: Exception) {
            error = exception.message ?: "PDF 문서를 열 수 없습니다."
        }
    }

    DisposableEffect(session) {
        val activeSession = session
        onDispose {
            activeSession?.let { closing ->
                // Closing waits for any in-progress page render on the renderer lock.
                Thread { runCatching { closing.close() } }.start()
            }
        }
    }

    when {
        error != null -> ReaderMessage(error!!, modifier)
        session == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> PdfPages(
            session = session!!,
            initialPage = initialPage,
            requestedPage = requestedPage,
            onPageChanged = onPageChanged,
            modifier = modifier,
        )
    }
}

@Composable
private fun PdfPages(
    session: PdfSession,
    initialPage: Int,
    requestedPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pageCount = session.pageCount

    LaunchedEffect(pageCount, initialPage) {
        if (pageCount > 0) listState.scrollToItem(initialPage.coerceIn(0, pageCount - 1))
    }
    LaunchedEffect(requestedPage, pageCount) {
        if (pageCount > 0 && requestedPage in 0 until pageCount &&
            requestedPage != listState.firstVisibleItemIndex
        ) {
            listState.animateScrollToItem(requestedPage)
        }
    }
    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> if (pageCount > 0) onPageChanged(page.coerceIn(0, pageCount - 1)) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color(0xFFE9EBF0)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(count = pageCount, key = { it }) { pageIndex ->
                PdfPage(
                    session = session,
                    pageIndex = pageIndex,
                    targetWidthPx = widthPx,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    session: PdfSession,
    pageIndex: Int,
    targetWidthPx: Int,
) {
    var bitmap by remember(session, pageIndex, targetWidthPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var zoom by remember(session, pageIndex) { mutableFloatStateOf(1f) }
    var initialDistance by remember(session, pageIndex) { mutableFloatStateOf(0f) }
    var initialZoom by remember(session, pageIndex) { mutableFloatStateOf(1f) }
    var isPinching by remember(session, pageIndex) { mutableStateOf(false) }
    var renderError by remember(session, pageIndex, targetWidthPx) { mutableStateOf<String?>(null) }

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        try {
            bitmap = withContext(Dispatchers.IO) { session.renderPage(pageIndex, targetWidthPx) }
        } catch (_: OutOfMemoryError) {
            renderError = "메모리가 부족해 이 페이지를 표시할 수 없습니다."
        } catch (error: Exception) {
            renderError = error.message ?: "페이지를 표시할 수 없습니다."
        }
    }
    DisposableEffect(bitmap) {
        val image = bitmap
        onDispose { image?.let { if (!it.isRecycled) it.recycle() } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(620.dp).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (renderError != null) {
                    Text(renderError!!, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            val transformState = rememberTransformableState { zoomChange, _, _ ->
                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
            }
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "PDF ${pageIndex + 1}페이지",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = zoom, scaleY = zoom)
                    .pointerInput(pageIndex) {
                        detectTapGestures(onDoubleTap = {
                            zoom = if (zoom > 1f) 1f else 2f
                        })
                    }
                    .pointerInput(pageIndex) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                if (changes.size >= 2) {
                                    val d = changes[0].position - changes[1].position
                                    val dist = hypot(d.x, d.y)
                                    if (!isPinching) {
                                        isPinching = true
                                        initialDistance = dist
                                        initialZoom = zoom
                                    } else if (initialDistance > 0f) {
                                        zoom = (initialZoom * (dist / initialDistance)).coerceIn(1f, 4f)
                                    }
                                    changes.forEach { it.consume() }
                                } else {
                                    if (isPinching) {
                                        isPinching = false
                                        initialDistance = 0f
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}

@Composable
fun ReaderMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
