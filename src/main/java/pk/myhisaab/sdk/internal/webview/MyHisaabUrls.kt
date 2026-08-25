package pk.myhisaab.sdk.internal.webview

import android.net.Uri
import pk.myhisaab.sdk.model.MyHisaabEnvironment

/**
 * Builds the login URL for a given environment + customer id, e.g.
 * `https://simosa.myhisaab.pk/login?customerId=<customerId>`. Query encoding is
 * handled by [Uri.Builder], so a customer id with reserved characters is escaped
 * safely.
 */
internal object MyHisaabUrls {
    fun loginUrl(environment: MyHisaabEnvironment, customerId: String): String =
        Uri.parse(environment.baseLoginUrl)
            .buildUpon()
            .appendQueryParameter("customerId", customerId)
            .build()
            .toString()
}
