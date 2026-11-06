package kr.docuview.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTypeDetectorTest {
    @Test
    fun detectsPdfHeader() {
        val header = "%PDF-1.7".encodeToByteArray()
        assertEquals(DocumentType.PDF, DocumentTypeDetector.detect("sample.pdf", header))
        assertEquals(DocumentType.PDF, DocumentTypeDetector.detect("sample.bin", byteArrayOf(0x00) + header))
    }

    @Test
    fun detectsHwpOleContainer() {
        val ole = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )
        assertEquals(DocumentType.HWP, DocumentTypeDetector.detect("sample.hwp", ole))
    }

    @Test
    fun detectsHwpxZipContainer() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(DocumentType.HWPX, DocumentTypeDetector.detect("sample.hwpx", zip))
    }

    @Test
    fun rejectsUnsupportedExtensionAndHeader() {
        assertNull(DocumentTypeDetector.detect("sample.docx", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(DocumentTypeDetector.detect("sample.hwp", "not a document".encodeToByteArray()))
    }
}
