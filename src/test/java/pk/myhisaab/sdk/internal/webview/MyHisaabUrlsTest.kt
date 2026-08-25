package pk.myhisaab.sdk.internal.webview

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.myhisaab.sdk.model.MyHisaabEnvironment

/**
 * Unit tests for [MyHisaabUrls.loginUrl].
 *
 * These run on the JVM via Robolectric, which provides a real `android.net.Uri`,
 * so the actual percent-encoding is exercised — not a mock.
 *
 * The central guarantee under test: **whatever the customer id, the produced URL
 * is a valid single-parameter URL whose decoded `customerId` equals the original
 * input.** That one property rules out breakage from spaces, `&`, `#`, `=`,
 * unicode, emoji, control characters, etc.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyHisaabUrlsTest {

    private val env = MyHisaabEnvironment.PRODUCTION
    private val expectedPrefix = "https://simosa.myhisaab.pk/login?customerId="

    /**
     * Asserts the full contract for one customer id:
     *  - correct scheme/host/path (the base URL is never corrupted)
     *  - exactly ONE query parameter, named `customerId` (no injection)
     *  - the decoded value round-trips back to the exact input
     *  - the URL contains no raw whitespace (nothing that would visually break it)
     */
    private fun assertValidUrl(customerId: String) {
        val url = MyHisaabUrls.loginUrl(env, customerId)

        assertTrue(
            "URL must start with the fixed base + customerId=, was: $url",
            url.startsWith(expectedPrefix),
        )
        assertFalse(
            "URL must not contain any raw whitespace, was: $url",
            url.any { it.isWhitespace() },
        )

        val parsed = Uri.parse(url)
        assertEquals("https", parsed.scheme)
        assertEquals("simosa.myhisaab.pk", parsed.host)
        assertEquals("/login", parsed.path)
        assertEquals(
            "Encoding must not create extra query parameters",
            setOf("customerId"),
            parsed.queryParameterNames,
        )
        assertEquals(
            "Decoded customerId must equal the original input",
            customerId,
            parsed.getQueryParameter("customerId"),
        )
    }

    // ---- Base / happy path ------------------------------------------------

    @Test
    fun `simple numeric id`() = assertValidUrl("12345")

    @Test
    fun `alphanumeric id with separators`() = assertValidUrl("cust_ABC-123.9")

    @Test
    fun `id with leading zeros is preserved`() = assertValidUrl("000123")

    @Test
    fun `exact output for a plain id`() {
        assertEquals(
            "https://simosa.myhisaab.pk/login?customerId=ABC123",
            MyHisaabUrls.loginUrl(env, "ABC123"),
        )
    }

    // ---- Whitespace -------------------------------------------------------

    @Test
    fun `single space is encoded not literal`() {
        val url = MyHisaabUrls.loginUrl(env, "john doe")
        assertFalse(url.contains(" "))
        assertTrue("space should encode to %20, was: $url", url.contains("%20"))
        assertEquals("john doe", Uri.parse(url).getQueryParameter("customerId"))
    }

    @Test
    fun `multiple and leading trailing spaces`() = assertValidUrl("  a  b   c  ")

    @Test
    fun `tab and newline and carriage return`() = assertValidUrl("a\tb\nc\rd")

    // ---- Query-injection safety ------------------------------------------

    @Test
    fun `ampersand does not start a new parameter`() {
        val url = MyHisaabUrls.loginUrl(env, "x&admin=true")
        val parsed = Uri.parse(url)
        assertEquals(setOf("customerId"), parsed.queryParameterNames)
        assertNull("must not inject an 'admin' param", parsed.getQueryParameter("admin"))
        assertEquals("x&admin=true", parsed.getQueryParameter("customerId"))
    }

    @Test
    fun `equals sign is encoded`() = assertValidUrl("key=value")

    @Test
    fun `hash does not create a fragment`() {
        val url = MyHisaabUrls.loginUrl(env, "abc#section")
        val parsed = Uri.parse(url)
        assertNull("must not create a URL fragment", parsed.fragment)
        assertEquals("abc#section", parsed.getQueryParameter("customerId"))
    }

    @Test
    fun `question mark inside value`() = assertValidUrl("a?b")

    // ---- Reserved / special characters -----------------------------------

    @Test
    fun `plus sign`() = assertValidUrl("a+b")

    @Test
    fun `percent sign`() = assertValidUrl("50%off")

    @Test
    fun `literal percent-encoding-looking text is double-encoded safely`() = assertValidUrl("%20%26")

    @Test
    fun `forward slashes`() = assertValidUrl("a/b/c")

    @Test
    fun `backslash`() = assertValidUrl("a\\b")

    @Test
    fun `colon and at`() = assertValidUrl("user:pass@host")

    @Test
    fun `double and single quotes`() = assertValidUrl("he said \"hi\" O'Brien")

    @Test
    fun `semicolon and comma`() = assertValidUrl("a;b,c")

    @Test
    fun `angle brackets and pipe`() = assertValidUrl("<a>|b")

    @Test
    fun `braces and brackets`() = assertValidUrl("{a}[b]")

    @Test
    fun `only special characters`() = assertValidUrl("&=#?/%+ ;,")

    // ---- Unicode ----------------------------------------------------------

    @Test
    fun `urdu script id`() = assertValidUrl("صارف-۱۲۳")

    @Test
    fun `arabic and accents`() = assertValidUrl("عميل José Müller")

    @Test
    fun `emoji in id`() = assertValidUrl("cust😀id")

    // ---- Boundaries -------------------------------------------------------

    @Test
    fun `empty id produces empty value not a broken url`() {
        val url = MyHisaabUrls.loginUrl(env, "")
        assertEquals("https://simosa.myhisaab.pk/login?customerId=", url)
        assertEquals("", Uri.parse(url).getQueryParameter("customerId"))
    }

    @Test
    fun `single space only`() = assertValidUrl(" ")

    @Test
    fun `very long id`() = assertValidUrl("a b&".repeat(500)) // 2000 chars, mixed reserved

    @Test
    fun `value that itself looks like a url`() =
        assertValidUrl("https://evil.com/login?customerId=hacked#x")

    // ---- Batch sweep over a broad set ------------------------------------

    @Test
    fun `round-trips for a broad sweep of inputs`() {
        val inputs = listOf(
            "12345", "cust_ABC-123", "john doe", " padded ", "a&b", "x&admin=true",
            "key=value", "abc#frag", "a+b", "50%off", "a/b/c", "a?b", "a:b",
            "user@host", "he\"llo", "O'Brien", "a;b", "a,b", "a b&c=d#e?f/g%h+i",
            "", " ", "\t\n", "صارف۱۲۳", "José", "😀", "%25%20", "a\\b", "<>|",
        )
        for (input in inputs) {
            assertValidUrl(input)
        }
    }

    // ---- Different ids are distinct --------------------------------------

    @Test
    fun `distinct ids produce distinct urls`() {
        assertFalse(
            MyHisaabUrls.loginUrl(env, "a b") == MyHisaabUrls.loginUrl(env, "a c"),
        )
    }
}
