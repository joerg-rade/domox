package domox.adapter.diagram

import domox.dom.crc.model.AssociationType
import domox.dom.crc.model.AttributeCdd
import domox.dom.crc.model.ClassCdd
import domox.dom.crc.model.CollaboratorCdd
import domox.dom.crc.model.ResponsibilityCdd
import org.assertj.core.api.Assertions.assertThat
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
    fun buildLexicalGraphDiagram_returnsAPdf() {
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
        assertThat(pdf).isNotNull()
        assertThat(pdf).describedAs("PDF response should not be empty").isNotEmpty()
        val magic = String(pdf, 0, minOf(5, pdf.size), StandardCharsets.ISO_8859_1)
        assertThat(magic).describedAs("Response should be a PDF, but began with '$magic'").isEqualTo("%PDF-")
    }

    /**
     * The class-diagram export's path: PlantUML assembled by the domain, rendered by Kroki's
     * `/plantuml/svg` and transcoded to PDF.  Uses the same PlantUML shape `generate.ClassDiagram`
     * produces (quoted class names, a quoted cardinality on an association), because the export's
     * output is what must survive the round trip — a source Kroki rejects would only show up as an
     * HTTP error here.
     */
    @Test //IntegrationTest
    fun buildPlantUmlDiagram_returnsAPdf() {
        //given — PlantUML source and a reachable Kroki
        System.setProperty("kroki.host", kroki.host)
        System.setProperty("kroki.port", kroki.getMappedPort(PORT).toString())
        val plantUml = """
            @startuml
            class "Store" as Store {
                stockLevel: int
                restock(): void
            }
            class "Till" as Till
            Store->"2"Till
            @enduml
        """.trimIndent()

        //when
        val pdf: ByteArray = DiagramBuilder().buildPlantUmlDiagram(plantUml)

        //then — a real PDF, not an HTTP error body
        assertThat(pdf).isNotNull()
        assertThat(pdf).describedAs("PDF response should not be empty").isNotEmpty()
        val magic = String(pdf, 0, minOf(5, pdf.size), StandardCharsets.ISO_8859_1)
        assertThat(magic).describedAs("Response should be a PDF, but began with '$magic'").isEqualTo("%PDF-")
    }

    /**
     * Every association type's arrow, <b>as the export itself emits it</b>, must be parseable by
     * PlantUML — a single unknown arrow makes PlantUML reject the whole diagram with
     * `Error 400: Syntax Error?`, which is how the `GENERALIZATION` arrow `|>-` (not a PlantUML arrow)
     * kept every diagram containing an inheritance from rendering.  Hand-written source in the test
     * above cannot catch that: it has to be `CollaboratorCdd#toPlantUmlString` that produces the line.
     */
    @Test //IntegrationTest
    fun buildPlantUmlDiagram_acceptsEveryArrowTheExportEmits() {
        //given — one relation of each AssociationType, rendered by the domain's own renderer
        System.setProperty("kroki.host", kroki.host)
        System.setProperty("kroki.port", kroki.getMappedPort(PORT).toString())
        val source = ClassCdd("Source", mutableListOf<AttributeCdd>(), mutableListOf<ResponsibilityCdd>(),
            mutableListOf<CollaboratorCdd>())
        val target = ClassCdd("Target", mutableListOf<AttributeCdd>(), mutableListOf<ResponsibilityCdd>(),
            mutableListOf<CollaboratorCdd>())
        val relations = AssociationType.entries.map { type ->
            CollaboratorCdd("rel", source, target).apply { setType(type) }.toPlantUmlString()
        }
        val plantUml = "@startuml\n" + relations.joinToString("\n") + "\n@enduml"

        //when
        val pdf: ByteArray = DiagramBuilder().buildPlantUmlDiagram(plantUml)

        //then
        val magic = String(pdf, 0, minOf(5, pdf.size), StandardCharsets.ISO_8859_1)
        assertThat(magic)
            .describedAs("PlantUML rejected the export's own arrows: ${relations.joinToString(" | ")}")
            .isEqualTo("%PDF-")
    }

}