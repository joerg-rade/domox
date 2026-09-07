package domox.diagram

import domox.UmlUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
internal class UmlUtilsTest {


    companion object {
        private const val PORT = 8000

        @Container
        @JvmStatic
        val kroki = GenericContainer("yuzutech/kroki:latest")
            .withExposedPorts(PORT)
            .withEnv("KROKI_PLANTUML_JAVAFLAGS", "-Xmx2g")
            .waitingFor(LogMessageWaitStrategy()
                .withRegEx(".*Kroki server started successfully on port $PORT.*")
                .withStartupTimeout(java.time.Duration.ofSeconds(60)))
    }

    @Test //IntegrationTest
    fun testGenerateDiagram() {
        //given
        val pumlCode = "\"" +
                "participant BOB [[https://en.wiktionary.org/wiki/best_of_breed]]\\n" +
                "participant PITA [[https://en.wiktionary.org/wiki/PITA]]\\n" +
                "BOB -> PITA: sometimes is a" +
                "\""
        //when
        val host = kroki.host
        val port = kroki.getMappedPort(PORT)
        val svg: String = UmlUtils().generateDiagram(pumlCode, host, port)
        //then
        assertNotNull(svg)
        assertTrue(svg.isNotEmpty(), "SVG response should not be empty")
        assertTrue(svg.contains("svg") || svg.contains("<svg"), "Response should be SVG format")
    }

}
