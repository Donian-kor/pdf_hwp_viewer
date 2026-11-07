package kr.docuview.app.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kr.docuview.app.data.DocumentDao
import kr.docuview.app.data.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class DocumentImporter(
    private val context: Context,
    private val dao: DocumentDao,
) {
    suspend fun import(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val input = openInputStream(uri)
            ?: throw IOException("선택한 파일을 읽을 수 없습니다.")

        val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
        val tempFile = File.createTempFile("import_", ".part", documentsDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            input.use { source ->
                FileOutputStream(tempFile).buffered().use { output ->
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        // Apply the conservative HWP cap while copying; PDFs may be larger.
                        val limit = if (displayName.endsWith(".pdf", ignoreCase = true)) {
                            MAX_PDF_BYTES
                        } else {
                            MAX_HWP_BYTES
                        }
                        if (totalBytes > limit) {
                            throw IOException(
                                if (limit == MAX_PDF_BYTES) "PDF 파일은 최대 256MB까지 가져올 수 있습니다."
                                else "HWP/HWPX 파일은 메모리 보호를 위해 최대 64MB까지 지원합니다.",
                            )
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }

            if (totalBytes == 0L) throw IOException("빈 파일은 열 수 없습니다.")
            val header = ByteArray(1024)
            val headerLength = FileInputStream(tempFile).use { it.read(header) }.coerceAtLeast(0)
            val type = DocumentTypeDetector.detect(displayName, header.copyOf(headerLength))
                ?: throw IOException("지원하지 않는 형식이거나 파일이 손상되었습니다. PDF, HWP 5.0, HWPX만 지원합니다.")

            if (type != DocumentType.PDF && totalBytes > MAX_HWP_BYTES) {
                throw IOException("HWP/HWPX 파일은 최대 64MB까지 지원합니다.")
            }

            val id = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            val finalFile = File(documentsDir, "$id.${type.extension}")
            val existing = dao.getDocument(id)

            if (existing == null || !finalFile.isFile) {
                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
            }

            val now = System.currentTimeMillis()
            val document = existing?.copy(
                displayName = displayName,
                extension = type.extension,
                localPath = finalFile.absolutePath,
                sizeBytes = totalBytes,
                lastOpenedAt = now,
            ) ?: DocumentEntity(
                id = id,
                displayName = displayName,
                extension = type.extension,
                localPath = finalFile.absolutePath,
                sizeBytes = totalBytes,
                lastOpenedAt = now,
            )
            dao.upsertDocument(document)
            document
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "문서"
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path ?: return null
                    File(path).takeIf { it.isFile }?.inputStream()
                }
                ContentResolver.SCHEME_CONTENT -> context.contentResolver.openInputStream(uri)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_PDF_BYTES = 256L * 1024L * 1024L
        private const val MAX_HWP_BYTES = 64L * 1024L * 1024L
    }
}