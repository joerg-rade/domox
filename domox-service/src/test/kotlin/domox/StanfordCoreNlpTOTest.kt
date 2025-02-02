package domox

import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.TestCase.assertNotNull
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URISyntaxException

class StanfordCoreNlpTOTest {
    @Test
    fun shouldBeValid() {
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