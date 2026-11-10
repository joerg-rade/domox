package domox.adapter.diagram

import domox.GraphvizUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
internal class GraphvizUtilsTest {

    companion object {
        private const val PORT = 8000

        @Container
        @JvmStatic
        val kroki = GenericContainer("yuzutech/kroki:latest")
            .withExposedPorts(PORT)
            .withEnv("KROKI_PLANTUML_JAVAFLAGS", "-Xmx2g")
            .waitingFor(LogMessageWaitStrategy()
                .withRegEx(".*Kroki server started successfully on port $PORT.*")
                .withStartupTimeout(Duration.ofSeconds(60)))
    }

    @Test //IntegrationTest
    fun generateDiagram_returnsAnSvg() {
        //given
        val dotCode = "digraph { a -> b }"
        //when
        val svg: String = GraphvizUtils().generateDiagram(
            dotCode,
            host = kroki.host,
            port = kroki.getMappedPort(PORT)
        )
        //then
        assertThat(svg).isNotNull()
        assertThat(svg).describedAs("Response should be SVG format").contains("<svg")
    }

}
