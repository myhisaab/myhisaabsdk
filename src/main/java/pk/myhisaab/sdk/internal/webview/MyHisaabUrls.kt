package pk.myhisaab.sdk.internal.webview

import android.net.Uri
import pk.myhisaab.sdk.model.MyHisaabEnvironment

/**
 * Builds the login URL for a given environment + client id, e.g.
 * `https://app.myhisaab.pk/login?clientId=<clientId>`. Query encoding is handled
 * by [Uri.Builder], so a client id with reserved characters is escaped safely.
 */
internal object MyHisaabUrls {
    fun loginUrl(environment: MyHisaabEnvironment, clientId: String): String =
        Uri.parse(environment.baseLoginUrl)
            .buildUpon()
            .appendQueryParameter("clientId", clientId)
            .build()
            .toString()
}
