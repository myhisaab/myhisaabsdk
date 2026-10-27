package pk.myhisaab.sdk.internal.webview

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Unit tests for the file-handling half of [WebAppBridge]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAppBridgeTest {

    private val received = mutableListOf<Triple<String, String?, String?>>()
    private val bridge = WebAppBridge(
        onOpenCamera = {},
        onFileReceived = { payload, fileName, mimeType -> received += Triple(payload, fileName, mimeType) },
    )

    private fun runMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `downloadFile delivers base64, name and mime type on the main thread`() {
        bridge.downloadFile("JVBERi0=", "statement_monthly.pdf", "application/pdf")
        assertEquals(0, received.size) // posted, not delivered on the binder thread

        runMainLooper()

        assertEquals(listOf(Triple("JVBERi0=", "statement_monthly.pdf", "application/pdf")), received)
    }

    @Test
    fun `shareFile and processDataUrl deliver a data url without a mime type`() {
        bridge.shareFile("data:text/csv;base64,QQ==", "a.csv")
        bridge.processDataUrl("data:application/pdf;base64,QQ==", "b.pdf")

        runMainLooper()

        assertEquals(
            listOf(
                Triple("data:text/csv;base64,QQ==", "a.csv", null),
                Triple("data:application/pdf;base64,QQ==", "b.pdf", null),
            ),
            received,
        )
    }

    @Test
    fun `one-argument calls deliver the file without a name`() {
        bridge.shareFile("data:text/csv;base64,QQ==")
        bridge.processDataUrl("data:application/pdf;base64,QQ==")

        runMainLooper()

        assertEquals(
            listOf(
                Triple("data:text/csv;base64,QQ==", null, null),
                Triple("data:application/pdf;base64,QQ==", null, null),
            ),
            received,
        )
    }

    @Test
    fun `missing JS arguments arrive as nothing, not the text undefined`() {
        bridge.downloadFile("JVBERi0=", "undefined", "undefined")
        bridge.shareFile("undefined", "null")
        bridge.shareFile(null, null)

        runMainLooper()

        assertEquals(
            listOf(Triple("JVBERi0=", null, null), Triple("", null, null), Triple("", null, null)),
            received,
        )
    }
}
