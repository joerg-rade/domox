package domox

import java.io.BufferedReader
import java.io.InputStreamReader

class FileUtil {

     fun readFileFromResources(fileName: String): String {
        var answer = ""
        val inputStream = javaClass.classLoader.getResourceAsStream(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            answer += line
        }
        reader.close()
        return answer
    }

}