package domox.diagram

import domox.nlp.DocumentTO
import domox.nlp.StanfordCoreNlpAPI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
internal class TdDiagramTest {

    companion object {
        @Container
        @JvmStatic
        val coreNlp = GenericContainer(DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g")
    }

    @Test
    fun testBuild() {
        //given
        val scheme = "http"
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val text = "A language tape has a title language and level."
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)
        assertNotNull(coreDocument)
        val coreSentence = coreDocument.sentences.first()
        //when
        val pumlCode = TdDiagram.build(coreSentence)
        //then
        assertTrue(pumlCode.contains("rectangle \"A\" as W1"))
        assertTrue(pumlCode.contains("card \"DT\" as D1"))
        assertTrue(pumlCode.contains("D1 -d-( W1"))
        assertTrue(pumlCode.contains("D9 -r-> D10"))
        assertEquals(4, countOccurrencesOfIn("D1 ", pumlCode))
        assertEquals(4, countOccurrencesOfIn("D10 ", pumlCode))
        assertEquals(5, countOccurrencesOfIn("NN", pumlCode))
        assertEquals(2, countOccurrencesOfIn("compound", pumlCode))
        System.out.println(pumlCode)
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).filter { it == needle }.count()
    }

}
