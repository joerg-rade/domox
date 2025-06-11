package domox.diagram;

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class KrokiTest {

    companion object {
        private val KROKI_IMAGE = DockerImageName.parse("yuzutech/kroki")
    }

    @Test
    @Throws(Exception::class)
    fun testKrokiContainer() {
        GenericContainer(KROKI_IMAGE)
            .withExposedPorts(8000).use { kroki ->
                kroki.start()
                val krokiUrl = "http://" + kroki.getHost() + ":" + kroki.getMappedPort(8000) + "/plantuml/svg/"

                // Example PlantUML diagram
                val plantUmlCode = "@startuml\nAlice -> Bob: Hello\n@enduml"
                val postData = plantUmlCode.toByteArray(StandardCharsets.UTF_8)

                // Send request to Kroki
                val connection = URL(krokiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.write(postData)

                // Validate response
                Assertions.assertEquals(200, connection.responseCode)
            }
    }

}
