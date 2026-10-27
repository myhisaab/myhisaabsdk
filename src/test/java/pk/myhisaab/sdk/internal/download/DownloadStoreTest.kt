package pk.myhisaab.sdk.internal.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Unit tests for [DownloadStore]; plain JVM file I/O in a temporary folder. */
class DownloadStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private fun file(name: String, content: String = "Date,Amount\n") =
        DecodedFile(content.toByteArray(), DataUrlFile.MIME_CSV, name)

    @Test
    fun `saves into the directory, creating it`() {
        val dir = File(temp.root, "myhisaab/downloads")

        val saved = DownloadStore.save(dir, file("report.csv"), now)

        assertEquals(File(dir, "report.csv"), saved)
        assertArrayEquals("Date,Amount\n".toByteArray(), saved.readBytes())
    }

    @Test
    fun `replaces a file with the same name`() {
        val dir = temp.newFolder("downloads")
        DownloadStore.save(dir, file("report.csv", "old"), now)

        val saved = DownloadStore.save(dir, file("report.csv", "new"), now)

        assertEquals("new", saved.readText())
    }

    @Test
    fun `deletes files older than a day and keeps recent ones`() {
        val dir = temp.newFolder("downloads")
        val old = File(dir, "old.pdf").apply { writeText("x"); setLastModified(now - TimeUnit.HOURS.toMillis(25)) }
        val recent = File(dir, "recent.pdf").apply { writeText("x"); setLastModified(now - TimeUnit.HOURS.toMillis(1)) }

        DownloadStore.save(dir, file("new.csv"), now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun `refuses a name that would escape the directory`() {
        val dir = temp.newFolder("downloads")

        assertThrows(IllegalStateException::class.java) {
            DownloadStore.save(dir, file("../escaped.csv"), now)
        }
        assertFalse(File(temp.root, "escaped.csv").exists())
    }
}
