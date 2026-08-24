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
 * Methods annotated with [JavascriptInterface] run on a WebView binder thread,
 * NOT the main thread; UI-affecting work is marshalled to the main thread here.
 * Only the explicit `@JavascriptInterface` methods below are reachable from JS.
 */
internal class WebAppBridge(
    private val onOpenCamera: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openCamera() {
        mainHandler.post { onOpenCamera() }
    }

    @JavascriptInterface
    fun isCameraAvailable(): Boolean = true

    companion object {
        const val NAME = "AndroidBridge"
    }
}
