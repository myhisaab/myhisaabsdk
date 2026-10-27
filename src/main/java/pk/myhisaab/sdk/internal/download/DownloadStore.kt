package pk.myhisaab.sdk.internal.download

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Writes decoded downloads into the SDK's private cache folder, which the SDK's
 * FileProvider exposes (see `res/xml/myhisaab_file_paths.xml`). Needs no storage
 * permission.
 */
internal object DownloadStore {

    /** Relative to `Context.cacheDir`; namespaced so it can't clash with host-app files. */
    const val DIRECTORY = "myhisaab/downloads"

    private val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(1)

    /**
     * Saves [file] into [directory], replacing a file of the same name. Files
     * older than a day are deleted first — whichever app the user shared them
     * with has long since read them.
     */
    fun save(directory: File, file: DecodedFile, nowMillis: Long = System.currentTimeMillis()): File {
        directory.mkdirs()
        directory.listFiles()
            ?.filter { nowMillis - it.lastModified() > MAX_AGE_MILLIS }
            ?.forEach { it.delete() }

        val target = File(directory, file.fileName)
        // fileName is already a single sanitized segment; this guards against regressions.
        check(target.canonicalFile.parentFile == directory.canonicalFile) {
            "Refusing to write outside $directory"
        }
        target.writeBytes(file.bytes)
        return target
    }
}
