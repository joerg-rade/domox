package domox.adapter.diagram

import domox.port.DiagramPort
import domox.port.ExtendedDependencyTO
import org.springframework.stereotype.Component

/**
 * Adapter implementation of [DiagramPort] that renders diagrams via Kroki
 * (PlantUML and Graphviz endpoints) and converts them to PDF.
 * <p>
 * Delegates to [DiagramBuilder] for the actual rendering work.
 * </p>
 */
@Component
class KrokiDiagramAdapter(
    private val diagramBuilder: DiagramBuilder,
) : DiagramPort {

    override fun buildTypedDependencyDiagram(dependencies: List<ExtendedDependencyTO>): ByteArray {
        return diagramBuilder.buildTypedDependencyDiagram(dependencies)
    }

    override fun buildLexicalGraphDiagram(dotCode: String): ByteArray {
        return diagramBuilder.buildLexicalGraphDiagram(dotCode)
    }
}
