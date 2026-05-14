package domox.nlp

import domox.HttpRequest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Disabled("Requires Docker environment with CoreNLP container")
class HttpRequestTest {

    @Container
    private val coreNlp = GenericContainer("corenlp:latest")
        .withExposedPorts(9000)
        .withEnv("JVM_OPTS", "-Xmx4g")

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
