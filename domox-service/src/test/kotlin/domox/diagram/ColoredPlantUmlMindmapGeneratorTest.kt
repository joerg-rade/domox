package domox.diagram

import domox.nlp.DocumentTO
import domox.nlp.SentenceTO
import domox.nlp.StanfordCoreNlpAPI
import org.junit.jupiter.api.Assertions.*
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
        logDependencies(sentence)
        return sentence
    }

    @Test
    fun testBuildPetPhotography() {
        //given
        val text =
            "11 ) Pet photography : Some pet shops offer pet photography services to capture memorable moments with pets ."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertNotNull(sentence.enhancedPlusPlusDependencies)

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(sentence).generateMindmap()
        println(actualPuml)
        //then
        assertEquals(2, countOccurrencesOfIn("Pet", actualPuml))
        assertEquals(4, countOccurrencesOfIn("photography", actualPuml))
        assertEquals(2, countOccurrencesOfIn("Some", actualPuml))
        assertEquals(6, countOccurrencesOfIn("pet", actualPuml))
        assertEquals(2, countOccurrencesOfIn("pets", actualPuml))
        assertEquals(2, countOccurrencesOfIn("shops", actualPuml))
        assertEquals(2, countOccurrencesOfIn("offer", actualPuml))
        assertEquals(2, countOccurrencesOfIn("services", actualPuml))
        assertEquals(2, countOccurrencesOfIn("capture", actualPuml))
        assertEquals(2, countOccurrencesOfIn("memorable", actualPuml))
        assertEquals(2, countOccurrencesOfIn("moments", actualPuml))
        assertEquals(3, countOccurrencesOfIn("punct", actualPuml))
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).count { it == needle }
    }

    private fun logDependencies(sentence: SentenceTO) {
        val dependencies = sentence.enhancedPlusPlusDependencies
        for (dep in dependencies) {
            println(dep)
        }
    }

    @Test
    fun testBuildKuhn() {
        //given
        val text =
            "Though a new paradigm may possess few or none of the capabilities of its predecessor, it nevertheless preserves a large part of the concrete problem-solving activity that science has gained through its predecessors."
        val sentence: SentenceTO = invokeCoreNlp(text)
        //then
        assertNotNull(sentence.enhancedPlusPlusDependencies)

        //when
        val actualPuml = ColoredPlantUmlMindmapGenerator(sentence).generateMindmap()
        println(actualPuml)
        //then
        assertTrue(actualPuml.contains("paradigm"))
    }

}