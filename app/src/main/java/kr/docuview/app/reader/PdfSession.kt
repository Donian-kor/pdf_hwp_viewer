package kr.docuview.app.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

/** A single PdfRenderer is serialized because Android does not support parallel page access. */
class PdfSession(file: File) : Closeable {
    private val lock = Any()
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(pageIndex: Int, requestedWidthPx: Int): Bitmap = synchronized(lock) {
        require(pageIndex in 0 until renderer.pageCount) { "페이지 범위를 벗어났습니다." }
        val page = renderer.openPage(pageIndex)
        try {
            val sourceWidth = page.width.coerceAtLeast(1)
            val sourceHeight = page.height.coerceAtLeast(1)
            val targetWidth = requestedWidthPx.coerceIn(480, MAX_RENDER_WIDTH_PX)
            val widthScale = targetWidth.toFloat() / sourceWidth
            val pixelRatio = MAX_PAGE_PIXELS.toDouble() / (sourceWidth.toLong() * sourceHeight).toDouble()
            val pixelScale = sqrt(pixelRatio).toFloat()
            val scale = min(widthScale, pixelScale).coerceAtMost(MAX_SCALE)
            val bitmapWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (error: Throwable) {
            throw error
        } finally {
            page.close()
        }
    }

    override fun close() {
        synchronized(lock) {
            renderer.close()
            descriptor.close()
        }
    }

    private companion object {
        const val MAX_RENDER_WIDTH_PX = 2048
        const val MAX_PAGE_PIXELS = 4_000_000L
        const val MAX_SCALE = 4.0f
    }
}
