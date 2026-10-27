package pk.myhisaab.sdk.internal.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * JavaScript <-> native bridge for the WebView, mirroring the iOS
 * `WKScriptMessageHandler` contract.
 *
 * The web app opens the native camera overlay via:
 * ```js
 * window.AndroidBridge.openCamera();
 * ```
 * Native returns the captured photo to the page via the SAME callback the iOS
 * app uses, so a single web integration works on both platforms:
 * ```js
 * window.receiveCameraImage('data:image/jpeg;base64,....');
 * ```
 *
 * The web app hands a PDF/CSV report to native (decoded, then offered in the
 * share sheet) straight from the statement API response — the Android side of
 * the iOS `downloadHandler` message:
 * ```js
 * window.AndroidBridge.downloadFile(base64, 'statement_monthly.pdf', 'application/pdf');
 * ```
 * `shareFile` / `processDataUrl` (data URL + optional name) are also accepted,
 * matching the MyHisaab host app.
 *
 * Methods annotated with [JavascriptInterface] run on a WebView binder thread,
 * NOT the main thread; UI-affecting work is marshalled to the main thread here.
 * Only the explicit `@JavascriptInterface` methods below are reachable from JS.
 */
internal class WebAppBridge(
    private val onOpenCamera: () -> Unit,
    private val onFileReceived: (payload: String, fileName: String?, mimeType: String?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openCamera() {
        mainHandler.post { onOpenCamera() }
    }

    @JavascriptInterface
    fun isCameraAvailable(): Boolean = true

    /**
     * The web app's download call: bare base64 plus the file name and MIME type,
     * exactly as the statement API returns them.
     */
    @JavascriptInterface
    fun downloadFile(base64: String?, fileName: String?, mimeType: String?) {
        deliverFile(base64, fileName, mimeType)
    }

    /**
     * Receives a report file: a `data:` URL or bare base64, plus an optional file
     * name (which also decides the type for bare base64).
     */
    @JavascriptInterface
    fun shareFile(dataUrl: String?, fileName: String?) {
        deliverFile(dataUrl, fileName, mimeType = null)
    }

    // WebView matches bridge methods by argument count, so a call without a file
    // name needs its own overload or JS gets "Method not found".
    @JavascriptInterface
    fun shareFile(dataUrl: String?) {
        deliverFile(dataUrl, fileName = null, mimeType = null)
    }

    /** Same as [shareFile]; also the callback the blob-download script uses. */
    @JavascriptInterface
    fun processDataUrl(dataUrl: String?, fileName: String?) {
        deliverFile(dataUrl, fileName, mimeType = null)
    }

    @JavascriptInterface
    fun processDataUrl(dataUrl: String?) {
        deliverFile(dataUrl, fileName = null, mimeType = null)
    }

    private fun deliverFile(payload: String?, fileName: String?, mimeType: String?) {
        // A missing or `undefined` JS argument arrives as the string "undefined".
        val data = payload.takeUnless { it.isJsNothing() }.orEmpty()
        val name = fileName.takeUnless { it.isJsNothing() }
        val type = mimeType.takeUnless { it.isJsNothing() }
        mainHandler.post { onFileReceived(data, name, type) }
    }

    private fun String?.isJsNothing(): Boolean = this == null || this == "undefined" || this == "null"

    companion object {
        const val NAME = "AndroidBridge"
    }
}
