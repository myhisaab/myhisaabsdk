package pk.myhisaab.sdk.internal.download

import android.net.Uri
import android.util.Base64

/** A report file from the web app, decoded and ready to write to disk. */
internal class DecodedFile(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

/**
 * Decodes the files the web app hands to native for download — the CSV and PDF
 * reports the backend returns as base64 — and picks a safe file name.
 *
 * Accepts a base64 data URL (`data:application/pdf;base64,…`, as produced by
 * `FileReader.readAsDataURL`), a percent-encoded data URL
 * (`data:text/csv;charset=utf-8,…`) or bare base64. Pure logic, no I/O.
 */
internal object DataUrlFile {

    const val MIME_PDF = "application/pdf"
    const val MIME_CSV = "text/csv"

    private val CSV_MIME_TYPES = setOf(MIME_CSV, "application/csv", "text/comma-separated-values")
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private const val RESERVED_NAME_CHARS = "<>:\"|?*"
    private const val MAX_BASE_NAME_LENGTH = 100

    /**
     * @param payload what the page sent: a data URL or bare base64.
     * @param suggestedFileName the page's file name, if any. Also decides the
     *   type when nothing else does (bare base64, `application/octet-stream`).
     * @param declaredMimeType the type the page passed separately, as
     *   `AndroidBridge.downloadFile` does; a data URL's own type takes precedence.
     * @throws IllegalArgumentException if the payload is empty, isn't valid
     *   base64, isn't a PDF or CSV, or is labelled PDF but isn't one.
     */
    fun decode(
        payload: String,
        suggestedFileName: String?,
        declaredMimeType: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): DecodedFile {
        val trimmed = payload.trim()
        require(trimmed.isNotEmpty()) { "Empty file payload" }

        var declaredMime: String? = declaredMimeType?.normalizedMimeType()
        var isBase64 = true
        var data = trimmed
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val comma = trimmed.indexOf(',')
            require(comma != -1) { "Data URL has no ',' separator" }
            val header = trimmed.substring("data:".length, comma)
            declaredMime = header.normalizedMimeType() ?: declaredMime
            isBase64 = header.split(';').any { it.trim().equals("base64", ignoreCase = true) }
            data = trimmed.substring(comma + 1)
        }

        val mimeType = resolveMimeType(declaredMime, suggestedFileName)
            ?: throw IllegalArgumentException(
                "Not a PDF or CSV (type=$declaredMime, name=$suggestedFileName)"
            )

        val bytes = if (isBase64) {
            decodeBase64(data)
        } else {
            Uri.decode(data).toByteArray(Charsets.UTF_8)
        }
        require(bytes.isNotEmpty()) { "File payload decoded to 0 bytes" }
        if (mimeType == MIME_PDF) {
            require(bytes.startsWith(PDF_MAGIC)) { "Payload is labelled PDF but is not a PDF" }
        }

        return DecodedFile(bytes, mimeType, fileNameFor(suggestedFileName, mimeType, nowMillis))
    }

    /**
     * Turns the page's file name into one safe file name whose extension matches
     * [mimeType]. Anything path-like is cut down to its last segment, so a name
     * such as `../../x.pdf` can never leave the downloads folder. Falls back to
     * `statement_<timestamp>`, the host app's convention.
     */
    fun fileNameFor(suggested: String?, mimeType: String, nowMillis: Long): String {
        val extension = if (mimeType == MIME_PDF) "pdf" else "csv"
        var base = suggested.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() && it !in RESERVED_NAME_CHARS }
            .trim()
        if (base.endsWith(".$extension", ignoreCase = true)) {
            base = base.dropLast(extension.length + 1)
        }
        base = base.trimStart('.').take(MAX_BASE_NAME_LENGTH).trim()
        if (base.isEmpty()) base = "statement_$nowMillis"
        return "$base.$extension"
    }

    private fun resolveMimeType(declaredMime: String?, fileName: String?): String? = when {
        declaredMime == MIME_PDF -> MIME_PDF
        declaredMime != null && declaredMime in CSV_MIME_TYPES -> MIME_CSV
        else -> when (fileName?.trim()?.substringAfterLast('.', "")?.lowercase()) {
            "pdf" -> MIME_PDF
            "csv" -> MIME_CSV
            else -> null
        }
    }

    private fun decodeBase64(data: String): ByteArray {
        val compact = data.filterNot { it.isWhitespace() }
        // android.util.Base64 silently skips characters outside its alphabet, so
        // garbage would "decode"; check the alphabet up front instead.
        require(compact.trimEnd('=').all { it.isBase64Char() }) { "Payload is not valid base64" }
        val urlSafe = compact.any { it == '-' || it == '_' }
        return Base64.decode(compact, if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT)
    }

    /** `"Text/CSV; charset=utf-8"` → `"text/csv"`; blank → null. */
    private fun String.normalizedMimeType(): String? = substringBefore(';').trim().lowercase().ifEmpty { null }

    private fun Char.isBase64Char(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "+/-_"

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
