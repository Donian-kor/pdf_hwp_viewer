package kr.docuview.app.reader

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder

@Composable
fun HwpReaderView(
    filePath: String,
    documentId: String,
    initialPage: Int,
    requestedPage: Int,
    onPageChanged: (Int, Int) -> Unit,
    onPageCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
    annotationEnabled: Boolean = false,
    annotationTool: String = "PEN",
    annotationColorHex: String = "#FF3B30",
    annotationsJson: String = "[]",
    onStroke: (pageIndex: Int, tool: String, colorHex: String, pointsJson: String) -> Unit = { _, _, _, _ -> },
    currentPageForUndo: Int = 0,
    undoRequest: Int = 0,
    clearRequest: Int = 0,
    shareRequest: Int = 0,
    sharePages: List<Int> = emptyList(),
    onSharedImages: (List<ByteArray>) -> Unit = {},
) {
    var webView by remember(documentId) { mutableStateOf<WebView?>(null) }
    var loaded by remember(documentId) { mutableStateOf(false) }
    var renderError by remember(documentId) { mutableStateOf<String?>(null) }
    // Tracks only pages the app explicitly asked the viewer to jump to. User scrolling
    // must not update this, otherwise the viewer would fight the user's scroll.
    var lastRequestedPage by remember(documentId) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var totalPages by remember(documentId) { mutableIntStateOf(0) }
    // Accumulates per-page PNGs while a multi-page share batch is running.
    val pendingShareImages = remember(documentId) { mutableStateListOf<ByteArray>() }
    val mainHandler = remember(documentId) { android.os.Handler(android.os.Looper.getMainLooper()) }
    val currentOnSharedImages by rememberUpdatedState(onSharedImages)
    fun flushPendingShare() {
        val collected = pendingShareImages.toList()
        pendingShareImages.clear()
        if (collected.isNotEmpty()) currentOnSharedImages(collected)
    }
    val flushRunnable = remember(documentId) {
        object : Runnable {
            override fun run() = flushPendingShare()
        }
    }

    val bridge = remember(documentId) {
        HwpJavascriptBridge(
            onLoadedCallback = { count ->
                loaded = true
                totalPages = count
                onPageCount(count)
            },
            onPageChangedCallback = { page, count ->
                if (count > 0) totalPages = count
                onPageChanged(page, count.coerceAtLeast(0))
            },
            onErrorCallback = { message -> renderError = message },
            onStrokeCallback = onStroke,
            onSharedImageCallback = { data ->
                // One PNG per call; flush shortly after the last page arrives.
                if (data.isNotBlank()) {
                    runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull()?.let { bytes ->
                        pendingShareImages.add(bytes)
                        mainHandler.removeCallbacks(flushRunnable)
                        mainHandler.postDelayed(flushRunnable, 1200L)
                    }
                }
            },
            onSharedPageBatchDoneCallback = {
                mainHandler.removeCallbacks(flushRunnable)
                mainHandler.post(flushRunnable)
            },
            onSharedImagesCallback = { /* legacy multi-payload path, unused */ },
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            createHwpWebView(
                context = viewContext,
                file = File(filePath),
                documentId = documentId,
                initialPage = initialPage,
                bridge = bridge,
            ).also { view ->
                webView = view
                view.evaluateJavascript(
                    "window.loadAnnotations(${"'" + annotationsJson.replace("'", "\\'") + "'"});",
                    null,
                )
            }
        },
        update = { view ->
            if (loaded) {
                view.evaluateJavascript(
                    "window.setAnnotationState($annotationEnabled, '$annotationTool', '$annotationColorHex');",
                    null,
                )
                val safeRequestedPage = if (totalPages > 0) requestedPage.coerceIn(0, totalPages - 1) else requestedPage.coerceAtLeast(0)
                if (safeRequestedPage != lastRequestedPage) {
                    lastRequestedPage = safeRequestedPage
                    view.evaluateJavascript("window.scrollToPage(${safeRequestedPage})", null)
                }
            }
        },
    )

    // Undo / clear must fire once per request, not on every recomposition.
    LaunchedEffect(undoRequest, loaded) {
        val view = webView
        if (view != null && loaded && undoRequest > 0) {
            view.evaluateJavascript("window.undoLastAnnotation($currentPageForUndo);", null)
        }
    }

    LaunchedEffect(clearRequest, loaded) {
        val view = webView
        if (view != null && loaded && clearRequest > 0) {
            view.evaluateJavascript("window.clearAllAnnotations();", null)
        }
    }

    LaunchedEffect(shareRequest, sharePages, loaded) {
        val view = webView
        if (view != null && loaded && shareRequest > 0) {
            pendingShareImages.clear()
            // Empty array → the WebView picks every page that actually has annotations.
            view.evaluateJavascript("window.requestSharedPages([]);", null)
        }
    }

    LaunchedEffect(webView, loaded) {
        val view = webView
        if (view != null && loaded) {
            view.evaluateJavascript("window.__docuviewPageSync = true;", null)
            view.evaluateJavascript(
                "window.setAnnotationState($annotationEnabled, '$annotationTool', '$annotationColorHex');",
                null,
            )
        }
    }

    LaunchedEffect(annotationsJson) {
        val view = webView
        if (view != null && loaded) {
            val escaped = annotationsJson.replace("'", "\\'")
            view.evaluateJavascript("window.loadAnnotations('$escaped');", null)
        }
    }

    DisposableEffect(documentId) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("AndroidBridge")
                loadUrl("about:blank")
                clearHistory()
                destroy()
            }
            webView = null
        }
    }

    if (renderError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("한글 문서를 열 수 없습니다.\n$renderError", color = MaterialTheme.colorScheme.error)
        }
    } else if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/** Captures the current WebView surface as PNG bytes (fallback when JS canvas is tainted). */
@SuppressLint("WrongThread")
private fun captureWebViewToPng(view: WebView): ByteArray {
    val bitmap = android.graphics.Bitmap.createBitmap(
        view.width.coerceAtLeast(1),
        view.height.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    view.draw(android.graphics.Canvas(bitmap))
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
    bitmap.recycle()
    return stream.toByteArray()
}

@SuppressLint("SetJavaScriptEnabled")
private fun createHwpWebView(
    context: Context,
    file: File,
    documentId: String,
    initialPage: Int,
    bridge: HwpJavascriptBridge,
): WebView {
    return WebView(context).apply {
        setBackgroundColor(android.graphics.Color.rgb(233, 235, 240))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            safeBrowsingEnabled = true
        }
        addJavascriptInterface(bridge, "AndroidBridge")
        webViewClient = LocalHwpWebViewClient(context.applicationContext, file)
        val html = context.assets.open("hwpviewer/index.html").bufferedReader().use { it.readText() }
        val baseUrl = "$LOCAL_ORIGIN/?documentId=$documentId&page=$initialPage"
        loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }
}

private class HwpJavascriptBridge(
    // Callback properties use a distinct "Callback" suffix so they never shadow the
    // @JavascriptInterface methods below (which would cause self-recursion).
    private val onLoadedCallback: (Int) -> Unit,
    private val onPageChangedCallback: (Int, Int) -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onStrokeCallback: (Int, String, String, String) -> Unit,
    private val onSharedImageCallback: (String) -> Unit,
    private val onSharedPageBatchDoneCallback: (Int) -> Unit,
    private val onSharedImagesCallback: (List<String>) -> Unit,
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onDocumentLoaded(pageCount: Int) {
        mainHandler.post { onLoadedCallback(pageCount.coerceAtLeast(0)) }
    }

    @JavascriptInterface
    fun onError(message: String) {
        mainHandler.post { onErrorCallback(message.take(500)) }
    }

    @JavascriptInterface
    fun onPageChanged(pageIndex: Int, pageCount: Int) {
        mainHandler.post {
            onPageChangedCallback(pageIndex.coerceAtLeast(0), pageCount.coerceAtLeast(0))
        }
    }

    @JavascriptInterface
    fun onStroke(pageIndex: Int, tool: String, color: String, pointsJson: String) {
        mainHandler.post {
            onStrokeCallback(pageIndex.coerceAtLeast(0), tool, color, pointsJson)
        }
    }

    @JavascriptInterface
    fun onSharedPage(base64Png: String) {
        mainHandler.post { onSharedImageCallback(base64Png) }
    }

    /** Called once after every page of a share batch has been sent. */
    @JavascriptInterface
    fun onSharedPageBatchDone(sentCount: Int) {
        mainHandler.post { onSharedPageBatchDoneCallback(sentCount) }
    }

    /** Accepts a JSON array of base64 PNG strings for multi-page sharing. */
    @JavascriptInterface
    fun onSharedPages(base64ListJson: String) {
        val list = runCatching {
            val array = org.json.JSONArray(base64ListJson)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index, "").takeIf { it.isNotBlank() }
            }
        }.getOrElse {
            // Fallback: naive split (should rarely be hit with valid JSON).
            base64ListJson.trim('[', ']').split(',')
                .map { it.trim('"') }
                .filter { it.isNotBlank() }
        }
        mainHandler.post { onSharedImagesCallback(list) }
    }
}

/** Serves only bundled viewer assets and the one selected file; all other requests are denied. */
private class LocalHwpWebViewClient(
    private val context: Context,
    private val documentFile: File,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
        val uri = request?.url ?: return forbiddenResponse()
        if (uri.scheme != "https" || uri.host != LOCAL_HOST) return forbiddenResponse()

        val path = URLDecoder.decode(uri.encodedPath ?: "/", Charsets.UTF_8.name())
        return when {
            path == "/document" && request.method.equals("GET", ignoreCase = true) -> {
                if (!documentFile.isFile) notFoundResponse() else response(
                    mimeType = "application/octet-stream",
                    stream = FileInputStream(documentFile),
                )
            }
            path.startsWith("/assets/") || path == "/rhwp_bg.wasm" -> {
                val relative = path.removePrefix("/")
                runCatching {
                    val stream = context.assets.open("hwpviewer/$relative")
                    response(mimeTypeFor(path), stream)
                }.getOrElse { notFoundResponse() }
            }
            else -> notFoundResponse()
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

    private fun response(mimeType: String, stream: InputStream): WebResourceResponse =
        WebResourceResponse(
            mimeType,
            if (mimeType.startsWith("text/") || mimeType == "image/svg+xml") "UTF-8" else null,
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to LOCAL_ORIGIN,
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
            ),
            stream,
        )

    private fun forbiddenResponse() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
        ByteArrayInputStream("Blocked".toByteArray()),
    )

    private fun notFoundResponse() = WebResourceResponse(
        "text/plain", "UTF-8", 404, "Not Found", emptyMap(),
        ByteArrayInputStream("Not found".toByteArray()),
    )

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "js" -> "text/javascript"
        "css" -> "text/css"
        "wasm" -> "application/wasm"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "woff", "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }
}

private const val LOCAL_HOST = "docviewer.local"
private const val LOCAL_ORIGIN = "https://$LOCAL_HOST"