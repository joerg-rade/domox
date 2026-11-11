package domox.dom.requirements;

import domox.dom.rdf.RdfGraphDiagrams;
import domox.dom.rdf.RdfProperties;
import domox.dom.rdf.UmlLayoutDiagrams;
import domox.port.RdfGraphTO;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import domox.port.RdfTripleTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level guardrail for the RDF/OWL → D3 ontology search on {@link OntologyDiagrams}.
 * <p>
 * The end-to-end path over a real ontology (Jena load → SPARQL → JSON) is covered by
 * {@code JenaRdfAdapterTest} in {@code domox-adapter} (the module that has Jena on the classpath);
 * this one pins the local/remote execution and the empty-graph behaviour without a Semantic-Web
 * dependency.  The SPARQL <em>text</em> itself is pinned by {@code OntologyQueriesTest}.
 */
@ExtendWith(MockitoExtension.class)
class OntologyDiagramsTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

    @Mock RdfPort rdfPort;

    private OntologyDiagrams classUnderTest;

    /** Local mode by default: no endpoint configured, so the bundled ontology is queried. */
    private final RdfProperties rdfProperties = new RdfProperties();

    @BeforeEach
    void setUp() {
        classUnderTest = ontologyDiagramsWith(rdfProperties);
    }

    private OntologyDiagrams ontologyDiagramsWith(final RdfProperties properties) {
        return new OntologyDiagrams(rdfPort, new RdfGraphDiagrams(), new UmlLayoutDiagrams(), properties);
    }

    @Test
    void ontologyAsD3GraphRendersTheSearchNeighbourhood() {
        // given — the port reports one focus IRI and returns that resource's neighbourhood
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("\"buddy\"")))
                .thenReturn(List.of(Map.of("focus", PS + "Buddy")));
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of("ps", PS));
        when(rdfPort.construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE),
                contains("VALUES ?s { <" + PS + "Buddy> }")))
                .thenReturn(graph);

        // when
        final String json = classUnderTest.ontologyAsD3Graph("Buddy");

        // then — the neighbourhood is rendered as D3 JSON
        assertThat(json).contains("\"nodes\":[");
        assertThat(json).contains("\"id\":\"" + PS + "Buddy\"");
        assertThat(json).contains("\"source\":\"" + PS + "Buddy\"");
        assertThat(json).contains("\"name\":\"Buddy\"");
    }

    @Test
    void searchOntologyWithoutMatchesRendersEmptyGraph() {
        // given — the focus query finds nothing
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("does-not-exist")))
                .thenReturn(List.of());

        // when
        final String json = classUnderTest.ontologyAsD3Graph("does-not-exist");

        // then — an empty (but well-formed) graph, not the whole ontology
        assertThat(json).isEqualTo("{\"nodes\":[],\"links\":[],\"legend\":[]}");
        // ...and BOTH passes ran: the exact query, then the word-boundary fallback
        verify(rdfPort, times(2)).select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    @Test
    void ontologyAsD3GraphRendersTheWholeOntology() {
        // given — the parameterless entry point takes the "all triples" path (no SELECT issued)
        when(rdfPort.construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE),
                contains("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        // when
        final String json = classUnderTest.ontologyAsD3Graph();

        // then
        assertThat(json).contains("\"id\":\"" + PS + "Dog\"");
        assertThat(json).contains("\"legend\":[");
    }

    /**
     * The action must hand back a view model that <em>carries</em> the rendered graph, so the
     * Wicket viewer can draw it: an action returning a bare {@code String} would be shown as text.
     */
    @Test
    void searchActionReturnsAViewModelCarryingTheRenderedGraph() {
        // given — a blank term takes the "all triples" path
        when(rdfPort.construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        // when
        final OntologyDiagram viewModel = classUnderTest.searchOntologyAsD3Graph("");

        // then — the graph is already rendered, i.e. no injected collaborator is needed to display it
        assertThat(viewModel.getSearchTerm()).isEmpty();
        assertThat(viewModel.title()).startsWith("Ontology:");
        assertThat(viewModel.getDiagram()).contains("\"id\":\"" + PS + "Dog\"");
    }

    /** Editing the search term invalidates the cached graph so the next render re-derives it. */
    @Test
    void editingTheSearchTermInvalidatesTheCachedGraph() {
        // given — a blank term takes the "all triples" path, so only CONSTRUCT is needed
        when(rdfPort.construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        final OntologyDiagram viewModel = classUnderTest.searchOntologyAsD3Graph("");
        assertThat(viewModel.getDiagram()).as("first render").contains("\"id\":\"" + PS + "Dog\"");

        // when
        viewModel.setSearchTerm("Whiskers");

        // then — the stale cache was dropped; no injected service is present here, so the
        //        re-derivation cannot run and the empty fallback is returned
        assertThat(viewModel.getDiagram()).isEqualTo("{\"nodes\":[],\"links\":[],\"legend\":[]}");
    }

    /**
     * With an endpoint configured the whole pipeline goes remote: the focus query AND the triple
     * fetch are issued through {@link RdfPort#selectRemote}, and the local
     * {@code select}/{@code construct} pair is never touched.
     */
    @Test
    void remoteEndpointIsQueriedInsteadOfTheBundledOntology() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("SELECT DISTINCT ?focus"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(Map.of("focus", PS + "Whiskers")));
        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("isLiteral(?o) AS ?lit"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(
                        Map.of("s", PS + "Whiskers", "p", RDF_TYPE, "o", PS + "Cat", "lit", "false"),
                        Map.of("s", PS + "Whiskers", "p", RDFS_LABEL, "o", "Whiskers the Siamese", "lit", "true")));

        final String json = diagrams.ontologyAsD3Graph("Whiskers");

        // the rows were rebuilt into a graph: an IRI object stays a resource, a literal stays a literal
        assertThat(json).contains("\"id\":\"" + PS + "Whiskers\"");
        assertThat(json).contains("\"id\":\"" + PS + "Cat\"");
        assertThat(json).contains("\"name\":\"Whiskers the Siamese\"");

        verify(rdfPort, never()).select(anyString(), anyString());
        verify(rdfPort, never()).construct(anyString(), anyString());
    }

    /** A public endpoint holds billions of triples, so every remote query must be bounded. */
    @Test
    void remoteQueriesCarryTheConfiguredLimit() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setQueryLimit(42);
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        diagrams.ontologyAsD3Graph("anything");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertThat(queries.getAllValues()).as("every remote query must be bounded")
                .allMatch(query -> query.contains("LIMIT 42"));
    }

    /**
     * {@code domox.rdf.local-resource} overrides the bundled ontology — so a deployment can point at
     * its own file (classpath resource or filesystem path) without rebuilding.
     */
    @Test
    void localResourceOverridesTheBundledOntology() {
        final RdfProperties local = new RdfProperties();
        local.setLocalResource("/opt/ontologies/custom.ttl");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(local);

        when(rdfPort.construct(eq("/opt/ontologies/custom.ttl"), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        final String json = diagrams.ontologyAsD3Graph("");

        assertThat(json).contains("\"id\":\"" + PS + "Dog\"");
        verify(rdfPort, never()).construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    /** A blank {@code local-resource} falls back to the bundled ontology. */
    @Test
    void blankLocalResourceUsesTheBundledOntology() {
        final RdfProperties local = new RdfProperties();
        local.setLocalResource("   ");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(local);

        when(rdfPort.construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(), Map.of()));

        diagrams.ontologyAsD3Graph("");

        verify(rdfPort).construct(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    /** The configured timeout and User-Agent must reach the port — an endpoint must not hang us. */
    @Test
    void remoteQueriesCarryTheConfiguredTimeoutAndUserAgent() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setQueryTimeoutSeconds(7);
        remote.setUserAgent("domox-test/9");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        diagrams.ontologyAsD3Graph("anything");

        final ArgumentCaptor<RdfRemoteOptions> options = ArgumentCaptor.forClass(RdfRemoteOptions.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), anyString(), options.capture());
        assertThat(options.getValue().timeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(options.getValue().userAgent()).isEqualTo("domox-test/9");
    }

    /**
     * A failing endpoint must not surface as a raw exception: the view model carries the reason and
     * the graph renders empty (this is what DBpedia's 502s looked like).
     */
    @Test
    void aFailingEndpointIsReportedOnTheViewModelInsteadOfThrowing() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class)))
                .thenThrow(new RuntimeException(new IllegalStateException("502 Bad Gateway")));

        final OntologyDiagram viewModel = diagrams.searchOntologyAsD3Graph("Pet");

        assertThat(viewModel.getMessage()).as("the root cause and the source are reported")
                .isEqualTo("Could not query https://example.org/sparql: 502 Bad Gateway");
        assertThat(viewModel.getDiagram()).isEqualTo("{\"nodes\":[],\"links\":[],\"legend\":[]}");
        assertThat(viewModel.title()).contains("source unavailable");
    }

    /** The derived property ({@code Corpus#getOntologyDiagram}) must never throw. */
    @Test
    void aFailingEndpointYieldsAnEmptyGraphForTheDerivedProperty() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThat(diagrams.ontologyAsD3Graph("Pet")).isEqualTo("{\"nodes\":[],\"links\":[],\"legend\":[]}");
    }

    /**
     * A named-graph endpoint (such as the reference-ontology Fuseki image) has an <b>empty default
     * graph</b>, so every remote query must be scoped to the configured graphs — otherwise the search
     * silently returns nothing.  The focus query is scoped with {@code FROM <graph>}; the triple-fetch
     * queries use {@code FROM NAMED <graph>} so they can also report which graph each row came from.
     */
    @Test
    void remoteQueriesAreScopedToTheConfiguredNamedGraphs() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setDefaultGraphs(List.of("https://schema.org/", "http://BPMNbasedOntology"));
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        diagrams.ontologyAsD3Graph("Person");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertThat(queries.getAllValues()).as("every remote query must be scoped to the configured graphs")
                .allMatch(query -> query.contains(
                                "FROM <https://schema.org/> FROM <http://BPMNbasedOntology>")
                        || query.contains(
                                "FROM NAMED <https://schema.org/> FROM NAMED <http://BPMNbasedOntology>"));
    }

    /**
     * The graph-attribution path: a named-graph endpoint answers the triple query with a {@code ?g}
     * binding, and the rendered node carries the source ontology's short name — the diagram prints it
     * on the node's fourth label line.
     */
    @Test
    void remoteTriplesAreAttributedToTheirSourceOntology() {
        final String gistGraph = "https://w3id.org/semanticarts/ontology/gistCore";
        final String gist = "https://w3id.org/semanticarts/ns/ontology/gist/";
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setDefaultGraphs(List.of(gistGraph));
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("SELECT DISTINCT ?focus"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(Map.of("focus", gist + "Agreement")));
        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("isLiteral(?o) AS ?lit"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(Map.of(
                        "g", gistGraph,
                        "s", gist + "Agreement",
                        "p", RDF_TYPE,
                        "o", "http://www.w3.org/2002/07/owl#Class",
                        "lit", "false")));

        final String json = diagrams.ontologyAsD3Graph("Agreement");

        assertThat(json).contains("\"ontology\":\"gist\"");
    }

    /** With no {@code default-graphs} the query stays unscoped — correct for a single-default-graph endpoint. */
    @Test
    void remoteQueriesAreUnscopedWhenNoDefaultGraphsAreConfigured() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final OntologyDiagrams diagrams = ontologyDiagramsWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        diagrams.ontologyAsD3Graph("Person");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertThat(queries.getAllValues()).as("no dataset clause is expected")
                .noneMatch(query -> query.contains("FROM <"));
    }
}
