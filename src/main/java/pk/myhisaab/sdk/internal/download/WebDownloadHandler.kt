package pk.myhisaab.sdk.internal.download

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.myhisaab.sdk.R
import java.io.File

/**
 * Handles report downloads started by the web app, mirroring the host app: the
 * file is decoded, saved to the SDK's private cache and offered through the
 * share sheet (Files, Drive, WhatsApp, …). Failures show a toast rather than
 * doing nothing.
 *
 * Entry points, both on the main thread:
 *  - [onDownloadStart] — the WebView's `DownloadListener` (`<a download>` links
 *    to `blob:` or `data:` URLs).
 *  - [onFileReceived] — the `AndroidBridge.downloadFile` / `shareFile` /
 *    `processDataUrl` bridge methods; the blob script calls back into the last.
 */
internal class WebDownloadHandler(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun onDownloadStart(webView: WebView, url: String, contentDisposition: String?, mimeType: String?) {
        // Only trust a name the page actually supplied; guessFileName() invents
        // "downloadfile.bin" otherwise.
        val fileName = contentDisposition
            ?.takeIf { it.isNotBlank() }
            ?.let { URLUtil.guessFileName(url, it, mimeType) }
        when {
            url.startsWith("blob:", ignoreCase = true) ->
                webView.evaluateJavascript(BlobDownloadScript.build(url, fileName), null)

            url.startsWith("data:", ignoreCase = true) -> onFileReceived(url, fileName)

            else -> Log.w(TAG, "Ignoring a download that isn't a blob: or data: URL")
        }
    }

    fun onFileReceived(payload: String, fileName: String?, mimeType: String? = null) {
        scope.launch {
            try {
                val (file, savedMimeType) = withContext(Dispatchers.IO) {
                    val decoded = DataUrlFile.decode(payload, fileName, mimeType)
                    val directory = File(context.cacheDir, DownloadStore.DIRECTORY)
                    DownloadStore.save(directory, decoded) to decoded.mimeType
                }
                share(file, savedMimeType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save or share a downloaded file", e)
                Toast.makeText(context, R.string.myhisaab_download_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun share(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, context.getString(R.string.myhisaab_share_document))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private companion object {
        const val TAG = "MyHisaabDownload"

        /** Matches the `<provider>` authority in the SDK manifest. */
        const val FILE_PROVIDER_SUFFIX = ".myhisaab.fileprovider"
    }
}
