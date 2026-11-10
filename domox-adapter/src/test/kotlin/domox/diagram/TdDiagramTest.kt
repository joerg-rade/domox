package domox.adapter.diagram

import domox.port.DocumentTO
import domox.adapter.nlp.StanfordCoreNlpAPI
import org.assertj.core.api.Assertions.assertThat
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
    fun build_rendersTokensAndDependenciesAsPlantUml() {
        //given
        val scheme = "http"
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val text = "A language tape has a title language and level."
        val coreDocument: DocumentTO = StanfordCoreNlpAPI(scheme, host, port).annotate(text)
        assertThat(coreDocument).isNotNull()
        val coreSentence = coreDocument.sentences.first()
        //when
        val pumlCode = TdDiagram.build(coreSentence)
        //then
        assertThat(pumlCode).contains("rectangle \"A\" as W1")
        assertThat(pumlCode).contains("card \"DT\" as D1")
        assertThat(pumlCode).contains("D1 -d-( W1")
        assertThat(pumlCode).contains("D9 -r-> D10")
        assertThat(countOccurrencesOfIn("D1 ", pumlCode)).isEqualTo(4)
        assertThat(countOccurrencesOfIn("D10 ", pumlCode)).isEqualTo(4)
        assertThat(countOccurrencesOfIn("NN", pumlCode)).isEqualTo(5)
        assertThat(countOccurrencesOfIn("compound", pumlCode)).isEqualTo(2)
    }

    private fun countOccurrencesOfIn(needle: String, haystack: String): Int {
        return haystack.windowed(needle.length).filter { it == needle }.count()
    }

}
