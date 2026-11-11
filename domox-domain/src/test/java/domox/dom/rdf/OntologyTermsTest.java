package domox.dom.rdf;

import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(lookup.sourceAvailable()).as(lookup.message()).isTrue();
        assertThat(lookup.terms()).hasSize(1);
        final OntologyTerm term = lookup.terms().getFirst();
        assertThat(term.graph()).isEqualTo("https://schema.org/");
        assertThat(term.label()).isEqualTo("Person");
        assertThat(term.kind()).isEqualTo(OntologyTerm.Kind.CLASS);
        assertThat(term.superClass()).isEqualTo("Thing");
        assertThat(term.definition()).isEqualTo("A person.");

        // the focus query must carry the FROM clause, or a named-graph dataset matches nothing
        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertThat(queries.getAllValues()).as("the focus query must be scoped to the configured graphs")
                .anyMatch(q -> q.contains("FROM <https://schema.org/>"));
    }

    @Test
    void localLookupUsesTheBundledOntologyWithoutGraphScoping() {
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("SELECT DISTINCT ?focus")))
                .thenReturn(List.of(Map.of("focus", "http://x/Cat")));
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("VALUES ?term")))
                .thenReturn(List.of(Map.of("term", "http://x/Cat", "predicate", RDF_TYPE,
                        "value", RDFS_CLASS, "lit", "false")));

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Cat");

        assertThat(lookup.terms().getFirst().kind()).isEqualTo(OntologyTerm.Kind.CLASS);
        assertThat(lookup.terms().getFirst().label()).as("no label -> the IRI's local name").isEqualTo("Cat");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), queries.capture());
        assertThat(queries.getAllValues()).as("the bundled ontology is a single unnamed graph")
                .noneMatch(q -> q.contains("GRAPH ?g"));
    }

    @Test
    void anUnreachableSourceIsReportedNotThrown() {
        when(rdfPort.select(anyString(), anyString()))
                .thenThrow(new RuntimeException(new IllegalStateException("connection refused")));

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Cat");

        assertThat(lookup.sourceAvailable()).isFalse();
        assertThat(lookup.terms()).isEmpty();
        assertThat(lookup.message()).as(lookup.message()).contains("connection refused");
    }

    @Test
    void noMatchIsReportedAsAvailableButEmpty() {
        when(rdfPort.select(anyString(), contains("SELECT DISTINCT ?focus"))).thenReturn(List.of());

        final OntologyLookup lookup = termsWith(new RdfProperties()).lookup("Nope");

        assertThat(lookup.sourceAvailable()).isTrue();
        assertThat(lookup.hasMatches()).isFalse();
        assertThat(lookup.message()).as(lookup.message()).contains("No ontology term matches");
    }

    @Test
    void describeQueriesUseTheValuesClauseAndTheNamedGraph() {
        final String remote = OntologyTerms.describeRemoteQuery(List.of("http://x/A"));
        assertThat(remote).contains("GRAPH ?g");
        assertThat(remote).contains("VALUES ?term { <http://x/A> }");
        assertThat(remote).contains("isLiteral(?value) AS ?lit");

        final String local = OntologyTerms.describeLocalQuery(List.of("http://x/A"));
        assertThat(local).doesNotContain("GRAPH");
        assertThat(local).contains("VALUES ?term { <http://x/A> }");
    }

    @Test
    void anIriThatCouldBreakOutOfTheValuesClauseIsDropped() {
        final String query = OntologyTerms.describeLocalQuery(
                List.of("http://x/A> } UNION { ?s ?p ?o"));

        assertThat(query).doesNotContain("UNION");
    }
}
