package pk.myhisaab.sdk.internal.webview

import org.json.JSONObject

/**
 * Builds the JavaScript that hands the FCM registration token to the web app,
 * mirroring the iOS `injectFCMToken` contract so one web integration serves both
 * platforms:
 *
 * 1. `window.MYHISAAB_FCM_TOKEN = { token, platform }` — for web code that boots
 *    *after* injection and reads the global directly.
 * 2. A `myhisaab:fcm-token` CustomEvent — for web code already running that
 *    listens for late/rotated tokens.
 *
 * The payload is serialized with [JSONObject], never string-interpolated, so
 * quotes/backslashes in the token cannot break out of the script.
 */
internal object FcmTokenScript {

    private const val PLATFORM = "android"
    private const val EVENT_NAME = "myhisaab:fcm-token"

    fun build(token: String): String {
        val payload = JSONObject()
            .put("token", token)
            .put("platform", PLATFORM)
            .toString()

        return """
            (function() {
                var payload = $payload;
                window.MYHISAAB_FCM_TOKEN = payload;
                window.dispatchEvent(
                    new CustomEvent('$EVENT_NAME', { detail: payload })
                );
            })();
        """.trimIndent()
    }
}
