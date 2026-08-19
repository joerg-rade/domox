package domox.diagram

//import domox.FileUtil
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
internal class TdDiagramMindmapTest {

    companion object {
        @Container
        @JvmStatic
        val coreNlp = GenericContainer(DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g")!!
    }

    @Test
    fun testBuild() {
        //given
        val scheme = "http"
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val text = "A language tape has a title language and level."
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)
        val sentence = coreDocument.sentences[0]
        //logSentence(sentence)

        assertNotNull(coreDocument)
        val coreSentence = coreDocument.sentences.first()
        //when
        val actualPuml = TdDiagramMindmap.build(coreSentence)
        //println(actualPuml)
        //then
        assertEquals(1, countOccurrencesOfIn("+ S", actualPuml))
        assertEquals(2, countOccurrencesOfIn("++ NP", actualPuml))
        assertEquals(2, countOccurrencesOfIn("+++[#MAGENTA] DT", actualPuml))
        assertEquals(4, countOccurrencesOfIn("+++[#CYAN] NN", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++[#CHARTREUSE] VP", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++[#CHARTREUSE] VBZ", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++[#WHITE] CC", actualPuml))

        assertEquals(1, countOccurrencesOfIn("++++_ A", actualPuml))
        assertEquals(2, countOccurrencesOfIn("++++_ language", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ tape", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ has", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ a", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ title", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++_ and", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++_ level", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++_ .", actualPuml))
        //then
//        val expectedPuml = FileUtil().readFileFromResources("mindmap/expected.puml")
//        assertTrue(actualPuml.equals(expectedPuml))
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).count { it == needle }
    }

    private fun logSentence(sentence: SentenceTO) {
        val tokens = sentence.tokens
        for (token in tokens) {
            println(token)
        }
        val dependencies = sentence.enhancedPlusPlusDependencies
        for (dep in dependencies) {
            println(dep)
        }
    }

    @Test
    fun testBuildFox() {
        //given
        val scheme = "http"
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val text = "The quick brown fox jumps over the lazy dog."
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)
        val sentence = coreDocument.sentences[0]
        //logSentence(sentence)

        assertNotNull(coreDocument)
        val coreSentence = coreDocument.sentences.first()
        //when
        val actualPuml = TdDiagramMindmap.build(coreSentence)
        //println(actualPuml)
        //then
        assertEquals(1, countOccurrencesOfIn("+ S", actualPuml))
        assertEquals(2, countOccurrencesOfIn("++ NP", actualPuml))
        assertEquals(2, countOccurrencesOfIn("+++[#MAGENTA] DT", actualPuml))
        assertEquals(3, countOccurrencesOfIn("+++[#e3e3e3] JJ", actualPuml))
        assertEquals(2, countOccurrencesOfIn("+++[#CYAN] NN", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++[#CHARTREUSE] VP", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++[#CHARTREUSE] VBZ", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++[#LIGHTGREY] .", actualPuml))

        assertEquals(1, countOccurrencesOfIn("++++_ The", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ quick", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ brown", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ fox", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ jumps", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ over", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ the", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ lazy", actualPuml))
        assertEquals(1, countOccurrencesOfIn("++++_ dog", actualPuml))
        assertEquals(1, countOccurrencesOfIn("+++_ .", actualPuml))
    }

}
