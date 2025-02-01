package domox

import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.TestCase.assertNotNull
import okhttp3.internal.immutableListOf
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URISyntaxException

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
        val to2 = readFromFile("PetShopUseCases.json")
        assertNotNull(to2)
    }

    private fun readFromFile(fileName: String): StanfordCoreNlpTO {
        val content = FileUtil().readFileFromResources(fileName)
        val to = createTransferObject(content)
        return to
    }

    private fun createTransferObject(content: String): StanfordCoreNlpTO {
        val transferObject = try {
            val objectMapper = ObjectMapper()
            objectMapper.readValue(content, StanfordCoreNlpTO::class.java)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
        return transferObject
    }

}