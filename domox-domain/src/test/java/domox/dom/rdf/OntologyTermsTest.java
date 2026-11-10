package domox.dom.rdf;

import domox.dom.requirements.Corpora;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guardrail for {@link OntologyTerms} — the ontology evidence behind ontology-assisted review.
 * <p>
 * Pins the generated SPARQL (the graph scoping is what makes the lookup work at all against a
 * named-graph dataset) and the "never throws" contract, without a Semantic-Web dependency.
 */
@ExtendWith(MockitoExtension.class)
class OntologyTermsTest {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";

    @Mock RdfPort rdfPort;

    private OntologyTerms termsWith(final RdfProperties properties) {
        return new OntologyTerms(rdfPort, properties);
    }

    private static RdfProperties remote() {
        final RdfProperties properties = new RdfProperties();
        properties.setEndpointUrl("https://example.org/sparql");
        properties.setDefaultGraphs(List.of("https://schema.org/"));
        return properties;
    }

    @Test
    void remoteLookupScopesTheFocusQueryAndReportsTheSourceGraph() {
        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("SELECT DISTINCT ?focus"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(Map.of("focus", "https://schema.org/Person")));
        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("GRAPH ?g"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(
                        Map.of("g", "https://schema.org/", "term", "https://schema.org/Person",
                                "predicate", RDF_TYPE, "value", RDFS_CLASS, "lit", "false"),
                        Map.of("g", "https://schema.org/", "term", "https://schema.org/Person",
                                "predicate", RDFS_LABEL, "value", "Person", "lit", "true"),
                        Map.of("g", "https://schema.org/", "term", "https://schema.org/Person",
                                "predicate", RDFS_COMMENT, "value", "A person.", "lit", "true"),
                        Map.of("g", "https://schema.org/", "term", "https://schema.org/Person",
                                "predicate", RDFS_SUBCLASS_OF, "value", "https://schema.org/Thing", "lit", "false")));

        final OntologyLookup lookup = termsWith(remote()).lookup("Person");

        assertTrue(lookup.sourceAvailable(), lookup.message());
        assertEquals(1, lookup.terms().size());
        final OntologyTerm term = lookup.terms().get(0);
        assertEquals("https://schema.org/", term.graph());
        assertEquals("Person", term.label());
        assertEquals(OntologyTerm.Kind.CLASS, term.kind());
        assertEquals("Thing", term.superClass());
        assertEquals("A person.", term.definition());

        // the focus query must carry the FROM clause, or a named-graph dataset matches nothing
        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertTrue(queries.getAllValues().stream().anyMatch(q -> q.contains("FROM <https://schema.org/>")),
                "the focus query must be scoped to the configured graphs: " + queries.getAllValues());
    }

    @Test
    void localLookupUsesTheBundledOntologyWithoutGraphScoping() {
        when(rdfPort.select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("SELECT DISTINCT ?focus")))
                .thenReturn(List.of(Map.of("focus", "http://x/Cat")));
        when(rdfPort.select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("VALUES ?term")))
                .thenReturn(List.of(Map.of("term", "http://x/Cat", "predicate", RDF_TYPE,
                        "value", RDFS_CLASS, "lit", "false")));

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Cat");

        assertEquals(OntologyTerm.Kind.CLASS, lookup.terms().get(0).kind());
        assertEquals("Cat", lookup.terms().get(0).label(), "no label -> the IRI's local name");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), queries.capture());
        assertTrue(queries.getAllValues().stream().noneMatch(q -> q.contains("GRAPH ?g")),
                "the bundled ontology is a single unnamed graph: " + queries.getAllValues());
    }

    @Test
    void anUnreachableSourceIsReportedNotThrown() {
        when(rdfPort.select(anyString(), anyString()))
                .thenThrow(new RuntimeException(new IllegalStateException("connection refused")));

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Cat");

        assertFalse(lookup.sourceAvailable());
        assertTrue(lookup.terms().isEmpty());
        assertTrue(lookup.message().contains("connection refused"), lookup.message());
    }

    @Test
    void noMatchIsReportedAsAvailableButEmpty() {
        when(rdfPort.select(anyString(), contains("SELECT DISTINCT ?focus"))).thenReturn(List.of());

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Nope");

        assertTrue(lookup.sourceAvailable());
        assertFalse(lookup.hasMatches());
        assertTrue(lookup.message().contains("No ontology term matches"), lookup.message());
    }

    @Test
    void describeQueriesUseTheValuesClauseAndTheNamedGraph() {
        final String remote = OntologyTerms.describeRemoteQuery(List.of("http://x/A"));
        assertTrue(remote.contains("GRAPH ?g"), remote);
        assertTrue(remote.contains("VALUES ?term { <http://x/A> }"), remote);
        assertTrue(remote.contains("isLiteral(?value) AS ?lit"), remote);

        final String local = OntologyTerms.describeLocalQuery(List.of("http://x/A"));
        assertFalse(local.contains("GRAPH"), local);
        assertTrue(local.contains("VALUES ?term { <http://x/A> }"), local);
    }

    @Test
    void anIriThatCouldBreakOutOfTheValuesClauseIsDropped() {
        final String query = OntologyTerms.describeLocalQuery(
                List.of("http://x/A> } UNION { ?s ?p ?o"));

        assertFalse(query.contains("UNION"), query);
    }
}
