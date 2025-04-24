package domox.nlp

import domox.FileUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DocumentTOTest {
    @Test
    fun shouldBeValid() {
        val to = readFromFile("TheQuickBrownFoxJumpedOverTheLazyDuck.json")
        Assertions.assertNotNull(to)
        val to2 = readFromFile("PetShopUseCases.json")
        Assertions.assertNotNull(to2)
    }

    private fun readFromFile(fileName: String): DocumentTO {
        val content = FileUtil().readFileFromResources(fileName)
        val to = StanfordCoreNlpAPI.createTransferObject(content)
        return to
    }

}