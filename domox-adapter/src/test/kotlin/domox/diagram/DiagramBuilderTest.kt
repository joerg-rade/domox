package domox.diagram

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Duration

@Testcontainers
internal class DiagramBuilderTest {

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
    fun testBuildLexicalGraphDiagramReturnsPdf() {
        //given — DOT source (as produced by a lexical graph run), reachable Kroki
        System.setProperty("kroki.host", kroki.host)
        System.setProperty("kroki.port", kroki.getMappedPort(PORT).toString())
        // Representative of LexicalGraphGenerator output: graph-level layout settings plus
        // nodes carrying a POS-code second label line (guillemets + literal \n) and labelled
        // directed edges. Using the real DOT shape guards against regressions (e.g. an sfdp
        // layout whose overlap-removal Kroki's Graphviz cannot honour would 400 here).
        val dotCode = """
            digraph LexicalDependencyGraph {
                graph [layout = dot, ranksep = "2.0"];
                shop [label="shop\n«NN»", fillcolor="#3498DB", width=0.900, height=0.350];
                customer [label="customer\n«NN»", fillcolor="#3498DB", width=0.900, height=0.350];
                shop -> customer [label="nsubj"];
            }
        """.trimIndent()

        //when
        val pdf: ByteArray = DiagramBuilder().buildLexicalGraphDiagram(dotCode)

        //then — a real PDF was produced via Kroki's /graphviz endpoint (not an HTTP 400)
        assertNotNull(pdf)
        assertTrue(pdf.isNotEmpty(), "PDF response should not be empty")
        val magic = String(pdf, 0, minOf(5, pdf.size), StandardCharsets.ISO_8859_1)
        assertTrue(magic == "%PDF-", "Response should be a PDF, but began with '$magic'")
    }

}