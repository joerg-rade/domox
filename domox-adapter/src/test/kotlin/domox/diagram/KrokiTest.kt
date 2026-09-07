package domox.diagram

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Testcontainers
class KrokiTest {

    companion object {
        private val KROKI_IMAGE = DockerImageName.parse("yuzutech/kroki")
        private const val PORT = 8000

        @Container
        @JvmStatic
        val kroki: GenericContainer<*> = GenericContainer(KROKI_IMAGE)
            .withExposedPorts(PORT)
            .waitingFor(LogMessageWaitStrategy()
                .withRegEx(".*Kroki server started successfully on port $PORT.*")
                .withStartupTimeout(java.time.Duration.ofSeconds(60)))
    }

    @Test
    @Throws(Exception::class)
    fun testKrokiContainer() {
        val krokiUrl = "http://" + kroki.host + ":" + kroki.getMappedPort(PORT) + "/plantuml/svg/"

        // Example PlantUML diagram
        val plantUmlCode = "@startuml\nAlice -> Bob: Hello\n@enduml"
        val postData = plantUmlCode.toByteArray(StandardCharsets.UTF_8)

        // Send request to Kroki using modern HttpClient
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI(krokiUrl))
            .POST(HttpRequest.BodyPublishers.ofByteArray(postData))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        Assertions.assertEquals(200, response.statusCode())
    }

}
