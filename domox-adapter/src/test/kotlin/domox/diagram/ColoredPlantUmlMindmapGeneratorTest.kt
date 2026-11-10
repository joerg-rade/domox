package domox.adapter.diagram

import domox.port.DocumentTO
import domox.port.ExtendedDependencyFactory
import domox.port.SentenceTO
import domox.adapter.nlp.StanfordCoreNlpAPI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class ColoredPlantUmlMindmapGeneratorTest {

    companion object {
        @Container
        @JvmStatic
        val coreNlp =
            GenericContainer(DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
                .withExposedPorts(9000)
                .withEnv("JVM_OPTS", "-Xmx4g")!!
    }

    private fun invokeCoreNlp(text: String): SentenceTO {
        val scheme = "http"
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)

        val sentence = coreDocument.sentences[0]
        return sentence
    }

    @Test
    fun buildPetPhotography_mindmapContainsEveryToken() {
        //given
        val text =
            "11 ) Pet photography : Some pet shops offer pet photography services to capture memorable moments with pets ."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertThat(sentence.enhancedPlusPlusDependencies).isNotNull()

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(ExtendedDependencyFactory(sentence).getDependencies()).generateMindmap()
        //then
        assertThat(countOccurrencesOfIn("Pet", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("photography", actualPuml)).isEqualTo(4)
        assertThat(countOccurrencesOfIn("Some", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("pet", actualPuml)).isEqualTo(6)
        assertThat(countOccurrencesOfIn("pets", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("shops", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("offer", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("services", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("capture", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("memorable", actualPuml)).isEqualTo(2)
        assertThat(countOccurrencesOfIn("moments", actualPuml)).isEqualTo(2)
        //assertEquals(3, countOccurrencesOfIn("punct", actualPuml)) including the legend, it's 4
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).count { it == needle }
    }

    @Test
    fun buildsMindmapForTheKuhnSentence() {
        //given
        val text =
            "Though a new paradigm may possess few or none of the capabilities of its predecessor, it nevertheless preserves a large part of the concrete problem-solving activity that science has gained through its predecessors."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertThat(sentence.enhancedPlusPlusDependencies).isNotNull()

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(ExtendedDependencyFactory(sentence).getDependencies()).generateMindmap()
        //then
        assertThat(actualPuml).contains("paradigm")
    }

    @Test
    fun buildsMindmapForTheSkinnerSentence() {
        //given
        val text =
            "Men act upon the world, and change it, and are changed in turn by the consequences of their actions."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertThat(sentence.enhancedPlusPlusDependencies).isNotNull()

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(ExtendedDependencyFactory(sentence).getDependencies()).generateMindmap()
        //then
        assertThat(actualPuml).contains("consequences")
    }

    @Test
    fun buildsMindmapForTheMarxSentence() {
        //given
        val text =
            "The wealth of those societies in which the capitalist mode of production prevails, presents itself as an immense accumulation of commodities; the individual commodity appears as its elementary form ."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertThat(sentence.enhancedPlusPlusDependencies).isNotNull()

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(ExtendedDependencyFactory(sentence).getDependencies()).generateMindmap()
        //then
        assertThat(actualPuml).contains("production")
    }

}