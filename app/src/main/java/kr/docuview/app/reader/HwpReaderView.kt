package kr.docuview.app.reader

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
) {
    val context = LocalContext.current
    var webView by remember(documentId) { mutableStateOf<WebView?>(null) }
    var loaded by remember(documentId) { mutableStateOf(false) }
    var renderError by remember(documentId) { mutableStateOf<String?>(null) }
    var lastAppliedPage by remember(documentId) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }

    val bridge = remember(documentId) {
        HwpJavascriptBridge(
            onLoaded = { count ->
                loaded = true
                onPageCount(count)
            },
            onPageChanged = { page, count ->
                lastAppliedPage = page
                onPageChanged(page, count)
            },
            onError = { message -> renderError = message },
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
            ).also { webView = it }
        },
        update = { /* Page jumps are handled by the keyed effect below. */ },
    )

    LaunchedEffect(webView, requestedPage, loaded) {
        val view = webView
        if (view != null && loaded && requestedPage != lastAppliedPage) {
            lastAppliedPage = requestedPage
            view.evaluateJavascript("window.scrollToPage(${requestedPage.coerceAtLeast(0)})", null)
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
            javaScriptEnabled = true // Required by the local rhwp WebAssembly renderer.
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
    private val onLoaded: (Int) -> Unit,
    private val onPageChanged: (Int, Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun onDocumentLoaded(pageCount: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onLoaded(pageCount.coerceAtLeast(0))
        }
    }

    @JavascriptInterface
    fun onError(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { onError(message.take(500)) }
    }

    @JavascriptInterface
    fun onPageChanged(pageIndex: Int, pageCount: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onPageChanged(pageIndex.coerceAtLeast(0), pageCount.coerceAtLeast(0))
        }
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
