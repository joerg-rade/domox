package domox.port;

import java.util.List;

/**
 * Port interface for diagram rendering services (Kroki, PlantUML, Graphviz).
 * Defined in the domain so that domain services depend only on this interface,
 * not on concrete adapter implementations.
 */
public interface DiagramPort {

    /**
     * Builds a typed-dependency syntax diagram (PDF) from a list of extended dependencies.
     */
    byte[] buildTypedDependencyDiagram(List<ExtendedDependencyTO> dependencies);

    /**
     * Builds a document-wide lexical component diagram (PDF) from Graphviz DOT code.
     */
    byte[] buildLexicalGraphDiagram(String dotCode);

    /**
     * Renders PlantUML source as a PDF — the class-diagram export's format.
     *
     * <p>Unlike {@link #buildTypedDependencyDiagram(java.util.List)}, the source is assembled by the
     * <em>caller</em>: the CRC class diagrams are built in the domain ({@code generate.ClassDiagram}),
     * which is also where the review-aware "what is in the model" rules live, so the domain owns the
     * text and this port owns only the rendering (Kroki → SVG → PDF).</p>
     */
    byte[] buildPlantUmlDiagram(String plantUml);
}
