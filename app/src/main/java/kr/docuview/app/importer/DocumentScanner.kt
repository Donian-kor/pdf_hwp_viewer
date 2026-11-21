package kr.docuview.app.importer

import android.os.Environment
import java.io.File

/** Discovers PDF/HWP documents on external storage. */
object DocumentScanner {
    fun scan(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && result.size < 500 && visited < 200_000) {
            val dir = stack.removeLast()
            visited++
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.startsWith(".")) continue
                    stack.add(child)
                } else {
                    val name = child.name.lowercase()
                    if (name.endsWith(".pdf") || name.endsWith(".hwp") || name.endsWith(".hwpx")) {
                        result.add(child)
                    }
                }
            }
        }
        return result.sortedByDescending { it.lastModified() }
    }
}
