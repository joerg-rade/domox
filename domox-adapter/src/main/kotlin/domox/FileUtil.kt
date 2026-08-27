package domox

import java.io.InputStreamReader

class FileUtil {

    fun readFileFromResources(path: String): String {
        val inputStream = this::class.java.getResourceAsStream("/$path")
            ?: throw IllegalArgumentException("Datei nicht gefunden im Classpath: $path")
        return InputStreamReader(inputStream).use { it.readText() }
    }

}