package pk.myhisaab.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the production login host so an accidental edit to the base URL is caught
 * by CI rather than in the field. Pure JVM — no Android dependencies.
 */
class MyHisaabEnvironmentTest {

    @Test
    fun `production base login url is the simosa host`() {
        assertEquals(
            "https://simosa.myhisaab.pk/login",
            MyHisaabEnvironment.PRODUCTION.baseLoginUrl,
        )
    }

    @Test
    fun `every environment uses https and the login path`() {
        for (environment in MyHisaabEnvironment.entries) {
            val base = environment.baseLoginUrl
            assertTrue("must be https: $base", base.startsWith("https://"))
            assertTrue("must target /login: $base", base.endsWith("/login"))
            assertTrue("must be a myhisaab.pk host: $base", base.contains(".myhisaab.pk/"))
        }
    }
}
