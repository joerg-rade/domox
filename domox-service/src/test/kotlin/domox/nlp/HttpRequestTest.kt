package domox.nlp

import domox.HttpRequest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class HttpRequestTest {

    companion object {
        @Container
        @JvmStatic
        val coreNlp = GenericContainer(DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g")
    }

    @Test
    fun testInvokeCoreNLP() {
        val host = coreNlp.host
        val port = coreNlp.getMappedPort(9000)
        val parameters = "{\"annotators\":\"depparse\",\"outputFormat\":\"json\"}"
        // Assuming HttpRequest.invokeCoreNLP_Fuel can accept host and port
        val responseStr = HttpRequest().invokeCoreNLP_Fuel(SampleText.QBF, parameters, host, port)
        assertNotEquals("", responseStr)
        println(responseStr)
    }
}
