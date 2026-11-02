package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level guardrail for the RDF → D3 JSON mapping.  The end-to-end path over a real ontology
 * (Jena load → SPARQL → JSON, parsed as JSON) is covered by {@code JenaRdfAdapterTest} in
 * {@code domox-adapter}; this one pins the mapping rules and the JSON escaping without any
 * Semantic-Web dependency.
 */
class RdfGraphDiagramsTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";

    private final RdfGraphDiagrams diagrams = new RdfGraphDiagrams();

    @Test
    void mapsTriplesToNodesLinksAndLegend() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of("ps", PS));

        final String json = diagrams.renderDiagram(graph);

        assertTrue(json.startsWith("{"), json);
        assertTrue(json.contains("\"nodes\":["), json);
        assertTrue(json.contains("\"links\":["), json);
        assertTrue(json.contains("\"legend\":["), json);

        // a class clusters under its super-class; an individual under its rdf:type
        assertTrue(json.contains(
                "\"id\":\"" + PS + "Dog\",\"name\":\"Dog\",\"subdomain\":\"Pet\",\"pos\":\"Class\""), json);
        assertTrue(json.contains(
                "\"id\":\"" + PS + "Buddy\",\"name\":\"Buddy\",\"subdomain\":\"Dog\",\"pos\":\"Individual\""), json);

        // every statement becomes an edge, labelled with the predicate's local name
        assertTrue(json.contains(
                "\"source\":\"" + PS + "Dog\",\"target\":\"" + PS + "Pet\",\"label\":\"subClassOf\""), json);

        // all three node kinds occur -> one legend entry each
        assertTrue(json.contains("\"label\":\"Class\""), json);
        assertTrue(json.contains("\"label\":\"Individual\""), json);
        assertTrue(json.contains("\"label\":\"Literal\""), json);
    }

    @Test
    void escapesLabelsAndLeavesRootClassesUngrouped() {
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Quote", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.literal(PS + "Quote", RDFS_LABEL, "a \"quoted\" label")
        ), Map.of());

        final String json = diagrams.renderDiagram(graph);

        assertTrue(json.contains("\"name\":\"a \\\"quoted\\\" label\""), json);
        assertTrue(json.contains("\"subdomain\":\"\""), "a root class has no cluster group: " + json);
    }

    @Test
    void localNameHandlesHashSlashAndMissingIris() {
        assertEquals("Dog", RdfGraphDiagrams.localName(PS + "Dog"));
        assertEquals("Class", RdfGraphDiagrams.localName(OWL_CLASS));
        assertEquals("index.html", RdfGraphDiagrams.localName("http://example.org/index.html"));
        assertEquals("", RdfGraphDiagrams.localName(null));
    }
}
