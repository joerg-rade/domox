package domox.dom.rdf;

import domox.port.RdfGraphTO;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the JSON emission: the shape of a node entry, the legend, the drill-down
 * flag and the link filtering.  The same payload is asserted end-to-end in {@code RdfGraphDiagramsTest}
 * and parsed as real JSON by {@code JenaRdfAdapterTest}; this pins the writer on its own.
 */
class RdfDiagramJsonTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";

    @Test
    void aNodeEntryCarriesEveryFieldTheClientReads() {
        final String json = RdfDiagramJson.write(graph(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS)
        )), RdfGraphIndex.index(graph(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS)))));

        assertThat(json).contains("\"id\":\"" + PS + "Dog\"");
        assertThat(json).contains("\"name\":\"Dog\"");
        assertThat(json).contains("\"subdomain\":\"\"");
        assertThat(json).contains("\"pos\":\"Class\"");
        assertThat(json).contains("\"color\":\"" + RdfNodeKind.CLASS.color + "\"");
        assertThat(json).contains("\"r\":" + RdfNodeKind.CLASS.radius);
        assertThat(json).contains("\"ontology\":\"\"");
        assertThat(json).contains("\"approved\":true");
        assertThat(json).contains("\"isCandidateColumn\":false");
    }

    @Test
    void theLegendListsOnlyTheKindsThatOccur() {
        // a class hierarchy plus a literal: no individual anywhere in this graph
        final RdfGraphTO graph = graph(List.of(
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.literal(PS + "Dog", RDFS_LABEL, "Dog")));

        final String json = RdfDiagramJson.write(graph, RdfGraphIndex.index(graph));

        assertThat(json).contains(RdfNodeKind.CLASS.color);
        assertThat(json).contains(RdfNodeKind.LITERAL.color);
        assertThat(json).as("no individual in this graph").doesNotContain("\"label\":\"Individual\"");
    }

    @Test
    void anEmptyGraphHasThePlainShapeWithoutTheDrillDownFlag() {
        final String json = RdfDiagramJson.write(graph(List.of()), Map.of());

        assertThat(json).isEqualTo("{\"nodes\":[],\"links\":[],\"legend\":[]}");
        assertThat(json).doesNotContain("reSearch");
    }

    @Test
    void aNonEmptyGraphEnablesDrillDown() {
        final RdfGraphTO graph = graph(List.of(RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS)));

        assertThat(RdfDiagramJson.write(graph, RdfGraphIndex.index(graph))).contains(",\"reSearch\":true");
    }

    @Test
    void aLinkIsSkippedWhenEitherEndpointIsNotANode() {
        final RdfGraphTO graph = graph(List.of(
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet")));

        final Map<String, RdfDiagramNode> nodes = new LinkedHashMap<>(RdfGraphIndex.index(graph));
        nodes.remove(PS + "Pet");   // the endpoint the edge points at is gone

        final String json = RdfDiagramJson.write(graph, nodes);

        assertThat(json).contains("\"links\":[]");
    }

    @Test
    void duplicateStatementsProduceASingleLink() {
        final RdfGraphTO graph = graph(List.of(
                RdfTripleTO.resource(PS + "Dog", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet"),
                RdfTripleTO.resource(PS + "Pet", RDF_TYPE, OWL_CLASS),
                RdfTripleTO.resource(PS + "Dog", RDFS_SUBCLASS_OF, PS + "Pet")));

        final String json = RdfDiagramJson.write(graph, RdfGraphIndex.index(graph));

        assertThat(json.split("\"label\":\"subClassOf\"", -1)).as("one subClassOf edge").hasSize(2);
    }

    private static RdfGraphTO graph(final List<RdfTripleTO> triples) {
        return new RdfGraphTO(triples, Map.of());
    }
}
