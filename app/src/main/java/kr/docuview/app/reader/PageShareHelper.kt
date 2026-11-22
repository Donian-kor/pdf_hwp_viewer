package kr.docuview.app.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PageShareHelper {
    fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
        shareBitmaps(context, listOf(bitmap), fileName)
    }

    /** Shares one or more PNG images via ACTION_SEND_MULTIPLE (or ACTION_SEND for one). */
    fun shareBitmaps(context: Context, bitmaps: List<Bitmap>, baseFileName: String) {
        if (bitmaps.isEmpty()) return
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val files = bitmaps.mapIndexed { index, bitmap ->
            File(dir, "${baseFileName}_${index + 1}_$timestamp.png").also { file ->
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        val uris = files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        if (uris.size == 1) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "주석 페이지 공유"))
        } else {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "주석 페이지 공유"))
        }
    }
}