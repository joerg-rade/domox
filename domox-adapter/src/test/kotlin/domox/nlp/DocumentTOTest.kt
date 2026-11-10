package domox.adapter.nlp

import domox.util.FileUtil
import domox.port.DocumentTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentTOTest {
    @Test
    fun `parses the bundled CoreNLP fixtures into sentences`() {
        val quickBrownFox = readFromFile("TheQuickBrownFoxJumpedOverTheLazyDuck.json")
        assertThat(quickBrownFox.sentences).describedAs("TheQuickBrownFox fixture").isNotEmpty()

        val petShop = readFromFile("PetShopUseCases.json")
        assertThat(petShop.sentences).describedAs("PetShopUseCases fixture").isNotEmpty()
    }

    private fun readFromFile(fileName: String): DocumentTO {
        val content = FileUtil().readFileFromResources(fileName)
        val to = StanfordCoreNlpAPI.createTransferObject(content)
        return to
    }

}