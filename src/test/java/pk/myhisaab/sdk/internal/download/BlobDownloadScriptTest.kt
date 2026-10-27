package pk.myhisaab.sdk.internal.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Unit tests for [BlobDownloadScript]. Robolectric supplies the real `org.json`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlobDownloadScriptTest {

    @Test
    fun `fetches the blob and calls back through the bridge`() {
        val js = BlobDownloadScript.build("blob:https://simosa.myhisaab.pk/1234", "report.pdf")

        assertTrue(js.contains("window.AndroidBridge"))
        assertTrue(js.contains("fetch(\"blob:https:\\/\\/simosa.myhisaab.pk\\/1234\")"))
        assertTrue(js.contains("bridge.processDataUrl(reader.result || '', \"report.pdf\")"))
    }

    @Test
    fun `reports a failed read back to native so the user sees an error`() {
        val js = BlobDownloadScript.build("blob:x", null)

        assertTrue(js.contains(".catch(function () { bridge.processDataUrl('', null); })"))
    }

    @Test
    fun `quotes in the file name cannot break out of the script`() {
        val js = BlobDownloadScript.build("blob:x", "a'b\"c</script>.pdf")

        assertTrue(js.contains("\"a'b\\\"c<\\/script>.pdf\""))
        assertFalse(js.contains("'a'b"))
    }
}
