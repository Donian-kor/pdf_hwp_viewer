package kr.docuview.app.data

import android.content.Context
import java.io.File

/** Persists the last document scan so the browser can show results immediately. */
class DocumentScanCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadFiles(): List<File> =
        (prefs.getStringSet(KEY_PATHS, emptySet()) ?: emptySet())
            .map(::File)
            .filter { it.exists() }
            .sortedByDescending { it.lastModified() }

    fun hasCachedScan(): Boolean = prefs.getBoolean(KEY_SCANNED, false)

    fun save(files: List<File>) {
        prefs.edit()
            .putStringSet(KEY_PATHS, files.map { it.absolutePath }.toSet())
            .putBoolean(KEY_SCANNED, true)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "docuview_scan_cache"
        const val KEY_PATHS = "document_paths"
        const val KEY_SCANNED = "has_scanned"
    }
}
