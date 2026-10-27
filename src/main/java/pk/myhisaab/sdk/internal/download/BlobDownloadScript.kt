package pk.myhisaab.sdk.internal.download

import org.json.JSONObject
import pk.myhisaab.sdk.internal.webview.WebAppBridge

/**
 * Builds the JavaScript that reads a `blob:` download inside the page and hands
 * it back as a data URL through `AndroidBridge.processDataUrl(dataUrl, fileName)`;
 * native code can't fetch a page's blob URL itself.
 *
 * Values are quoted with [JSONObject.quote], never pasted in raw, so quotes in a
 * file name can't break out of the script. If the blob can't be read (e.g. the
 * page already revoked it), native is still called with an empty payload so the
 * user sees the error message.
 */
internal object BlobDownloadScript {

    fun build(blobUrl: String, fileName: String?): String {
        val url = JSONObject.quote(blobUrl)
        val name = fileName?.let { JSONObject.quote(it) } ?: "null"
        return """
            (function() {
                var bridge = window.${WebAppBridge.NAME};
                if (!bridge) return;
                fetch($url)
                    .then(function (response) { return response.blob(); })
                    .then(function (blob) {
                        var reader = new FileReader();
                        reader.onloadend = function () {
                            bridge.processDataUrl(reader.result || '', $name);
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(function () { bridge.processDataUrl('', $name); });
            })();
        """.trimIndent()
    }
}
