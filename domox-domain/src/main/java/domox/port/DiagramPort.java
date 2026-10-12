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
}
