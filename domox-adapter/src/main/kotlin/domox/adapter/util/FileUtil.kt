package domox.adapter.util

import java.io.InputStreamReader

/**
 * Utility for reading files from the classpath.
 * <p>
 * Lives in the adapter (not the domain) because classpath-resource access is an
 * infrastructure concern; the domain's own {@code domox.util.FileUtil} serves the same
 * purpose for domain-owned resources.
 * </p>
 */
class FileUtil {

    fun readFileFromResources(path: String): String {
        val inputStream = this::class.java.getResourceAsStream("/$path")
            ?: throw IllegalArgumentException("File not found in classpath: $path")
        return InputStreamReader(inputStream).use { it.readText() }
    }

}
