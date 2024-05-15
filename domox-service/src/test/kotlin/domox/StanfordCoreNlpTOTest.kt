package domox

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.internal.immutableListOf
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URISyntaxException
import kotlin.test.assertNotNull

class StanfordCoreNlpTOTest {
    @Test
    fun shouldBeValid() {
        val depList = immutableListOf(
            "ROOT",
            "punct",
            "dep",
            "compound",
            "amod",
            "nmod",
            "nmod:over",
            "appos",
            "det",
            "nsubj",
            "case",
            "parataxis",
            "obj",
            "cc",
            "conj",
            "conj:and",
            "conj:or"
        )
        val posList = immutableListOf(
            "-LRB-", "NNS", "SYM", "NN", ",", "FW", "'''", ":", "``", "VBZ", "VBD", "DT", "CC", "JJ", "IN", ".", "-RRB-"
        )

//        val text = "A language tape has a title language and level."
        val to = readFromFile("TheQuickBrownFoxJumpedOverTheLazyDuck.json")
        assertNotNull(to)
    }

    private fun readFromFile(fileName: String): StanfordCoreNlpTO {
        val to: StanfordCoreNlpTO = try {
            val content = readFileFromResources(fileName)
            val objectMapper = ObjectMapper()
            objectMapper.readValue(content, StanfordCoreNlpTO::class.java)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
        return to
    }

    private fun readFileFromResources(fileName: String): String {
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