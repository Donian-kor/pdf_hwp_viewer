package kr.docuview.app.importer

/** Supported local document formats. */
enum class DocumentType(val extension: String) {
    PDF("pdf"),
    HWP("hwp"),
    HWPX("hwpx"),
}

object DocumentTypeDetector {
    private val pdfSignature = "%PDF-".encodeToByteArray()
    private val oleSignature = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    /**
     * Detects the document using both extension and file signature.
     * PDF permits a small prefix before its header, so the first 1 KiB is inspected.
     */
    fun detect(fileName: String, header: ByteArray): DocumentType? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (containsAtMost(header, pdfSignature, 1024)) return DocumentType.PDF

        return when {
            extension == "hwp" && header.startsWith(oleSignature) -> DocumentType.HWP
            extension == "hwpx" && isZip(header) -> DocumentType.HWPX
            // Correct a misleading extension when the signature is unambiguous.
            extension == "hwp" && isZip(header) -> DocumentType.HWPX
            extension == "hwpx" && header.startsWith(oleSignature) -> DocumentType.HWP
            else -> null
        }
    }

    private fun containsAtMost(data: ByteArray, needle: ByteArray, maxOffset: Int): Boolean {
        val lastStart = minOf(data.size - needle.size, maxOffset)
        if (lastStart < 0) return false
        for (offset in 0..lastStart) {
            if (needle.indices.all { index -> data[offset + index] == needle[index] }) return true
        }
        return false
    }

    private fun isZip(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()))

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
