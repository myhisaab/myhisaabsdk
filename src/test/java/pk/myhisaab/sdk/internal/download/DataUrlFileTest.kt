package pk.myhisaab.sdk.internal.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64 as JvmBase64

/**
 * Unit tests for [DataUrlFile]. Robolectric supplies the real `android.util.Base64`
 * and `android.net.Uri`; payloads are encoded with the JVM's own Base64 so the
 * decoder is checked against an independent implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataUrlFileTest {

    private val now = 1_700_000_000_000L
    private val pdfBytes = "%PDF-1.7\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\n%%EOF".toByteArray()
    private val csvBytes = "Date,Amount,Note\n2026-10-01,500,Shoes\n".toByteArray()

    private fun b64(bytes: ByteArray) = JvmBase64.getEncoder().encodeToString(bytes)

    private fun decode(payload: String, name: String? = null, mimeType: String? = null) =
        DataUrlFile.decode(payload, name, mimeType, nowMillis = now)

    // ---- Happy paths ---------------------------------------------------------

    @Test
    fun `pdf data url decodes with default statement name`() {
        val file = decode("data:application/pdf;base64,${b64(pdfBytes)}")

        assertArrayEquals(pdfBytes, file.bytes)
        assertEquals(DataUrlFile.MIME_PDF, file.mimeType)
        assertEquals("statement_$now.pdf", file.fileName)
    }

    @Test
    fun `csv data url keeps the page's file name`() {
        val file = decode("data:text/csv;base64,${b64(csvBytes)}", "October report.csv")

        assertArrayEquals(csvBytes, file.bytes)
        assertEquals(DataUrlFile.MIME_CSV, file.mimeType)
        assertEquals("October report.csv", file.fileName)
    }

    @Test
    fun `csv data url with charset parameter`() {
        val file = decode("data:text/csv;charset=utf-8;base64,${b64(csvBytes)}")

        assertEquals(DataUrlFile.MIME_CSV, file.mimeType)
        assertArrayEquals(csvBytes, file.bytes)
    }

    @Test
    fun `bare base64 takes its type from the file name`() {
        val pdf = decode(b64(pdfBytes), "statement.pdf")
        val csv = decode(b64(csvBytes), "statement.CSV")

        assertEquals(DataUrlFile.MIME_PDF, pdf.mimeType)
        assertArrayEquals(pdfBytes, pdf.bytes)
        assertEquals(DataUrlFile.MIME_CSV, csv.mimeType)
        assertEquals("statement.csv", csv.fileName)
    }

    @Test
    fun `downloadFile payload - bare base64 with a separate mime type and name`() {
        // What the web app passes to AndroidBridge.downloadFile(base64, fileName, mimeType).
        val pdf = decode(b64(pdfBytes), "statement_monthly.pdf", "application/pdf")
        val csv = decode(b64(csvBytes), "statement_monthly.csv", "text/csv; charset=utf-8")

        assertEquals(DataUrlFile.MIME_PDF, pdf.mimeType)
        assertEquals("statement_monthly.pdf", pdf.fileName)
        assertArrayEquals(pdfBytes, pdf.bytes)
        assertEquals(DataUrlFile.MIME_CSV, csv.mimeType)
        assertEquals("statement_monthly.csv", csv.fileName)
    }

    @Test
    fun `a separate mime type is enough without a file name`() {
        val file = decode(b64(pdfBytes), null, "application/pdf")

        assertEquals("statement_$now.pdf", file.fileName)
    }

    @Test
    fun `a data url's own type wins over a separate mime type`() {
        val file = decode("data:application/pdf;base64,${b64(pdfBytes)}", null, "text/csv")

        assertEquals(DataUrlFile.MIME_PDF, file.mimeType)
    }

    @Test
    fun `a separate mime type other than pdf or csv falls back to the file name`() {
        assertEquals(DataUrlFile.MIME_CSV, decode(b64(csvBytes), "x.csv", "application/octet-stream").mimeType)
        assertThrows(IllegalArgumentException::class.java) { decode(b64(csvBytes), null, "image/png") }
    }

    @Test
    fun `octet-stream blob takes its type from the file name`() {
        // FileReader.readAsDataURL labels untyped blobs application/octet-stream.
        val file = decode("data:application/octet-stream;base64,${b64(csvBytes)}", "export.csv")

        assertEquals(DataUrlFile.MIME_CSV, file.mimeType)
    }

    @Test
    fun `base64 wrapped across lines decodes`() {
        val wrapped = JvmBase64.getMimeEncoder().encodeToString(pdfBytes + ByteArray(200) { 'x'.code.toByte() })
        assertTrue("precondition: encoder wrapped the output", wrapped.contains("\r\n"))

        val file = decode("data:application/pdf;base64,$wrapped")

        assertEquals(pdfBytes.size + 200, file.bytes.size)
    }

    @Test
    fun `url-safe base64 decodes`() {
        val bytes = pdfBytes + byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val urlSafe = JvmBase64.getUrlEncoder().encodeToString(bytes)
        assertTrue("precondition: uses - or _", urlSafe.contains('-') || urlSafe.contains('_'))

        assertArrayEquals(bytes, decode("data:application/pdf;base64,$urlSafe").bytes)
    }

    @Test
    fun `percent-encoded csv data url decodes`() {
        val file = decode("data:text/csv;charset=utf-8,Date%2CAmount%0A2026-10-01%2C500%2B")

        assertEquals("Date,Amount\n2026-10-01,500+", String(file.bytes))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        val file = decode("  data:application/pdf;base64,${b64(pdfBytes)}\n")

        assertArrayEquals(pdfBytes, file.bytes)
    }

    // ---- Rejections ----------------------------------------------------------

    @Test
    fun `empty and blank payloads are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { decode("", "a.pdf") }
        assertThrows(IllegalArgumentException::class.java) { decode("   ", "a.pdf") }
        assertThrows(IllegalArgumentException::class.java) { decode("data:application/pdf;base64,") }
    }

    @Test
    fun `garbled base64 is rejected rather than silently decoded`() {
        assertThrows(IllegalArgumentException::class.java) {
            decode("data:text/csv;base64,%%%not*base64!!")
        }
    }

    @Test
    fun `payload labelled pdf that is not a pdf is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            decode("data:application/pdf;base64,${b64(csvBytes)}")
        }
    }

    @Test
    fun `types other than pdf and csv are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            decode("data:image/png;base64,${b64(csvBytes)}")
        }
        assertThrows(IllegalArgumentException::class.java) { decode(b64(csvBytes), "notes.txt") }
        assertThrows(IllegalArgumentException::class.java) { decode(b64(csvBytes), null) }
    }

    @Test
    fun `data url without a comma is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { decode("data:application/pdf;base64") }
    }

    // ---- File names ----------------------------------------------------------

    private fun pdfName(suggested: String?) = DataUrlFile.fileNameFor(suggested, DataUrlFile.MIME_PDF, now)

    @Test
    fun `path traversal is cut down to the last segment`() {
        assertEquals("evil.xml.pdf", pdfName("../../shared_prefs/evil.xml"))
        assertEquals("x.pdf", pdfName("..\\..\\x.pdf"))
        assertEquals("x.pdf", pdfName("/data/data/pk.app/files/x.pdf"))
        assertEquals("statement_$now.pdf", pdfName("../.."))
    }

    @Test
    fun `hidden-file dots, control and reserved characters are removed`() {
        assertEquals("hidden.pdf", pdfName(".hidden.pdf"))
        assertEquals("abcdef.pdf", pdfName("a<b>c:d|e?f*\u0000.pdf"))
    }

    @Test
    fun `extension always matches the type`() {
        assertEquals("report.pdf", pdfName("report.PDF"))
        assertEquals("report.exe.pdf", pdfName("report.exe"))
        assertEquals("report.pdf.csv", DataUrlFile.fileNameFor("report.pdf", DataUrlFile.MIME_CSV, now))
    }

    @Test
    fun `blank names fall back to the statement default`() {
        assertEquals("statement_$now.pdf", pdfName(null))
        assertEquals("statement_$now.pdf", pdfName("   "))
        assertEquals("statement_$now.pdf", pdfName(".pdf"))
    }

    @Test
    fun `very long names are capped`() {
        val name = pdfName("a".repeat(500) + ".pdf")

        assertEquals(100 + ".pdf".length, name.length)
    }

    @Test
    fun `unicode names are kept`() {
        assertEquals("اکتوبر رپورٹ.pdf", pdfName("اکتوبر رپورٹ.pdf"))
    }
}
