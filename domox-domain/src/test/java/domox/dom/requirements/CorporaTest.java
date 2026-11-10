package domox.dom.requirements;

import domox.dom.crc.service.ClassArchetypeClassifier;
import domox.dom.crc.service.DomainModels;
import domox.dom.rdf.RdfGraphDiagrams;
import domox.dom.rdf.RdfProperties;
import domox.dom.rdf.UmlLayoutDiagrams;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.RuleMatches;
import domox.port.NlpPort;
import domox.port.RdfGraphTO;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import domox.port.RdfTripleTO;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;
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
 * Unit-level guardrail for the RDF/OWL → D3 ontology search on {@link Corpora}.
 * <p>
 * The end-to-end path over a real ontology (Jena load → SPARQL → JSON) is covered by
 * {@code JenaRdfAdapterTest} in {@code domox-adapter} (the module that has Jena on the classpath);
 * this one pins the generated SPARQL and the empty-graph behaviour without a Semantic-Web
 * dependency.
 */
@ExtendWith(MockitoExtension.class)
class CorporaTest {

    private static final String PS = "http://example.org/petshop#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

    @Mock RepositoryService repositoryService;
    @Mock FactoryService factoryService;
    @Mock RuleMatches ruleMatches;
    @Mock Documents documents;
    @Mock DomainModels domainModels;
    @Mock ClassArchetypeClassifier archetypeClassifier;
    @Mock CandidateResolver candidateResolver;
    @Mock NlpPort nlpPort;
    @Mock RdfPort rdfPort;

    private Corpora classUnderTest;

    /** Local mode by default: no endpoint configured, so the bundled ontology is queried. */
    private final RdfProperties rdfProperties = new RdfProperties();

    @BeforeEach
    void setUp() {
        classUnderTest = corporaWith(rdfProperties);
    }

    private Corpora corporaWith(final RdfProperties properties) {
        return new Corpora(
                repositoryService, factoryService, ruleMatches, documents, List.of(),
                domainModels, archetypeClassifier, candidateResolver, nlpPort,
                rdfPort, new RdfGraphDiagrams(), new UmlLayoutDiagrams(), properties);
    }

    @Test
    void ontologyAsD3GraphRendersTheSearchNeighbourhood() {
        // given — the port reports one focus IRI and returns that resource's neighbourhood
        when(rdfPort.select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("\"buddy\"")))
                .thenReturn(List.of(Map.of("focus", PS + "Buddy")));
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of("ps", PS));
        when(rdfPort.construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE),
                contains("VALUES ?s { <" + PS + "Buddy> }")))
                .thenReturn(graph);

        // when
        final String json = classUnderTest.ontologyAsD3Graph("Buddy");

        // then — the neighbourhood is rendered as D3 JSON
        assertTrue(json.contains("\"nodes\":["), json);
        assertTrue(json.contains("\"id\":\"" + PS + "Buddy\""), json);
        assertTrue(json.contains("\"source\":\"" + PS + "Buddy\""), json);
        assertTrue(json.contains("\"name\":\"Buddy\""), json);
    }

    @Test
    void searchOntologyWithoutMatchesRendersEmptyGraph() {
        // given — the focus query finds nothing
        when(rdfPort.select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("does-not-exist")))
                .thenReturn(List.of());

        // when
        final String json = classUnderTest.ontologyAsD3Graph("does-not-exist");

        // then — an empty (but well-formed) graph, not the whole ontology
        assertEquals("{\"nodes\":[],\"links\":[],\"legend\":[]}", json);
        // ...and BOTH passes ran: the exact query, then the word-boundary fallback
        verify(rdfPort, times(2)).select(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    @Test
    void ontologyAsD3GraphRendersTheWholeOntology() {
        // given — the parameterless entry point takes the "all triples" path (no SELECT issued)
        when(rdfPort.construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE),
                contains("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        // when
        final String json = classUnderTest.ontologyAsD3Graph();

        // then
        assertTrue(json.contains("\"id\":\"" + PS + "Dog\""), json);
        assertTrue(json.contains("\"legend\":["), json);
    }

    /**
     * The action must hand back a view model that <em>carries</em> the rendered graph, so the
     * Wicket viewer can draw it: an action returning a bare {@code String} would be shown as text.
     */
    @Test
    void searchActionReturnsAViewModelCarryingTheRenderedGraph() {
        // given — a blank term takes the "all triples" path
        when(rdfPort.construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        // when
        final OntologyDiagram viewModel = classUnderTest.searchOntologyAsD3Graph("");

        // then — the graph is already rendered, i.e. no injected collaborator is needed to display it
        assertEquals("", viewModel.getSearchTerm());
        assertTrue(viewModel.title().startsWith("Ontology:"), viewModel.title());
        assertTrue(viewModel.getDiagram().contains("\"id\":\"" + PS + "Dog\""), viewModel.getDiagram());
    }

    /** Editing the search term invalidates the cached graph so the next render re-derives it. */
    @Test
    void editingTheSearchTermInvalidatesTheCachedGraph() {
        // given — a blank term takes the "all triples" path, so only CONSTRUCT is needed
        when(rdfPort.construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        final OntologyDiagram viewModel = classUnderTest.searchOntologyAsD3Graph("");
        assertTrue(viewModel.getDiagram().contains("\"id\":\"" + PS + "Dog\""), "first render");

        // when
        viewModel.setSearchTerm("Whiskers");

        // then — the stale cache was dropped; no injected service is present here, so the
        //        re-derivation cannot run and the empty fallback is returned
        assertEquals("{\"nodes\":[],\"links\":[],\"legend\":[]}", viewModel.getDiagram());
    }

    @Test
    void neighbourhoodQueryRestrictsEachUnionBranchWithValues() {
        final String sparql = Corpora.neighbourhoodQuery(List.of("http://x/A", "http://x/B"));

        assertTrue(sparql.contains("CONSTRUCT { ?s ?p ?o }"), sparql);
        // the VALUES must be INSIDE each branch — in front of the union it binds nothing there
        assertTrue(sparql.contains("{ VALUES ?s { <http://x/A> <http://x/B> } ?s ?p ?o }"), sparql);
        assertTrue(sparql.contains("{ VALUES ?o { <http://x/A> <http://x/B> } ?s ?p ?o }"), sparql);
    }

    /**
     * The focus query must compare for <b>equality</b>: a substring {@code REGEX} is what made a
     * search for {@code Pet} also return {@code Petit}, {@code PetShow}, {@code competition}, …
     */
    @Test
    void focusQueryMatchesTheConceptExactlyNotBySubstring() {
        final String sparql = Corpora.focusQuery("Pet");

        assertTrue(sparql.contains("SELECT DISTINCT ?focus"), sparql);
        assertTrue(sparql.contains("LCASE(REPLACE(STR(?focus), \"^.*[/#]\", \"\")) = \"pet\""), sparql);
        assertTrue(sparql.contains("LCASE(STR(?l)) = \"pet\""), sparql);
        assertTrue(!sparql.contains("REGEX("), "the exact query must not use substring matching: " + sparql);
    }

    /** Labels may come from {@code skos:prefLabel} as well as {@code rdfs:label}. */
    @Test
    void focusQueryConsidersSkosPrefLabels() {
        final String sparql = Corpora.focusQuery("Pet");

        assertTrue(sparql.contains("?labelPred = rdfs:label || ?labelPred = skos:prefLabel"), sparql);
    }

    /**
     * A UML/OntoUML export names its elements with {@code ontouml:name} and carries no
     * {@code rdfs:label} at all, so the focus query must consider that predicate too — otherwise
     * every search over such a model resolves nothing and renders an empty diagram.
     *
     * @see domox.dom.rdf.RdfGraphDiagrams for the matching node-classification rules
     */
    @Test
    void focusQueryConsidersOntoumlNames() {
        final String sparql = Corpora.focusQuery("Commitment");

        assertTrue(sparql.contains("PREFIX ontouml: <https://w3id.org/ontouml#>"), sparql);
        assertTrue(sparql.contains("?labelPred = ontouml:name"), sparql);
    }

    /** A configured label language restricts label matching to that tag (or untagged labels). */
    @Test
    void focusQueryAppliesTheConfiguredLabelLanguage() {
        assertTrue(!Corpora.focusQuery("Pet", "").contains("LANG(?l)"),
                "no language configured -> no language filter");

        final String english = Corpora.focusQuery("Pet", "en");
        assertTrue(english.contains("LANG(?l) = \"en\" || LANG(?l) = \"\""), english);
    }

    /**
     * The fallback is a <b>word-boundary</b> match, so a partial phrase still resolves while
     * {@code Pet} cannot reach {@code Petit} (no boundary between "pet" and "it").
     */
    @Test
    void wordMatchQueryMatchesOnWordBoundaries() {
        final String sparql = Corpora.wordMatchQuery("Golden Retriever", "");

        assertTrue(sparql.contains("REGEX("), sparql);
        assertTrue(sparql.contains("(^|"), "must anchor on a leading boundary: " + sparql);
        assertTrue(sparql.contains("|$)"), "must anchor on a trailing boundary: " + sparql);
        assertTrue(sparql.contains("\\Q"), "the term must be quoted literally: " + sparql);
    }

    @Test
    void escapeSparqlStringNeutralisesQuotesAndBackslashes() {
        assertEquals("a \\\"b\\\"", Corpora.escapeSparqlString("a \"b\""));
        assertEquals("C:\\\\path", Corpora.escapeSparqlString("C:\\path"));
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
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("SELECT DISTINCT ?focus"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(Map.of("focus", PS + "Whiskers")));
        when(rdfPort.selectRemote(eq("https://example.org/sparql"), contains("isLiteral(?o) AS ?lit"),
                any(RdfRemoteOptions.class)))
                .thenReturn(List.of(
                        Map.of("s", PS + "Whiskers", "p", RDF_TYPE, "o", PS + "Cat", "lit", "false"),
                        Map.of("s", PS + "Whiskers", "p", RDFS_LABEL, "o", "Whiskers the Siamese", "lit", "true")));

        final String json = corpora.ontologyAsD3Graph("Whiskers");

        // the rows were rebuilt into a graph: an IRI object stays a resource, a literal stays a literal
        assertTrue(json.contains("\"id\":\"" + PS + "Whiskers\""), json);
        assertTrue(json.contains("\"id\":\"" + PS + "Cat\""), json);
        assertTrue(json.contains("\"name\":\"Whiskers the Siamese\""), json);

        verify(rdfPort, never()).select(anyString(), anyString());
        verify(rdfPort, never()).construct(anyString(), anyString());
    }

    /** A public endpoint holds billions of triples, so every remote query must be bounded. */
    @Test
    void remoteQueriesCarryTheConfiguredLimit() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setQueryLimit(42);
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        corpora.ontologyAsD3Graph("anything");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertTrue(queries.getAllValues().stream().allMatch(query -> query.contains("LIMIT 42")),
                "every remote query must be bounded: " + queries.getAllValues());
    }

    /**
     * {@code domox.rdf.local-resource} overrides the bundled ontology — so a deployment can point at
     * its own file (classpath resource or filesystem path) without rebuilding.
     */
    @Test
    void localResourceOverridesTheBundledOntology() {
        final RdfProperties local = new RdfProperties();
        local.setLocalResource("/opt/ontologies/custom.ttl");
        final Corpora corpora = corporaWith(local);

        when(rdfPort.construct(eq("/opt/ontologies/custom.ttl"), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(
                        RdfTripleTO.resource(PS + "Dog", RDF_TYPE, "http://www.w3.org/2002/07/owl#Class")),
                        Map.of("ps", PS)));

        final String json = corpora.ontologyAsD3Graph("");

        assertTrue(json.contains("\"id\":\"" + PS + "Dog\""), json);
        verify(rdfPort, never()).construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    /** A blank {@code local-resource} falls back to the bundled ontology. */
    @Test
    void blankLocalResourceUsesTheBundledOntology() {
        final RdfProperties local = new RdfProperties();
        local.setLocalResource("   ");
        final Corpora corpora = corporaWith(local);

        when(rdfPort.construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), contains("CONSTRUCT { ?s ?p ?o }")))
                .thenReturn(new RdfGraphTO(List.of(), Map.of()));

        corpora.ontologyAsD3Graph("");

        verify(rdfPort).construct(eq(Corpora.DEFAULT_ONTOLOGY_RESOURCE), anyString());
    }

    /** The configured timeout and User-Agent must reach the port — an endpoint must not hang us. */
    @Test
    void remoteQueriesCarryTheConfiguredTimeoutAndUserAgent() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setQueryTimeoutSeconds(7);
        remote.setUserAgent("domox-test/9");
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        corpora.ontologyAsD3Graph("anything");

        final ArgumentCaptor<RdfRemoteOptions> options = ArgumentCaptor.forClass(RdfRemoteOptions.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), anyString(), options.capture());
        assertEquals(Duration.ofSeconds(7), options.getValue().timeout());
        assertEquals("domox-test/9", options.getValue().userAgent());
    }

    /**
     * A failing endpoint must not surface as a raw exception: the view model carries the reason and
     * the graph renders empty (this is what DBpedia's 502s looked like).
     */
    @Test
    void aFailingEndpointIsReportedOnTheViewModelInsteadOfThrowing() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class)))
                .thenThrow(new RuntimeException(new IllegalStateException("502 Bad Gateway")));

        final OntologyDiagram viewModel = corpora.searchOntologyAsD3Graph("Pet");

        assertEquals("Could not query https://example.org/sparql: 502 Bad Gateway",
                viewModel.getMessage(), "the root cause and the source are reported");
        assertEquals("{\"nodes\":[],\"links\":[],\"legend\":[]}", viewModel.getDiagram());
        assertTrue(viewModel.title().contains("source unavailable"), viewModel.title());
    }

    /** The derived property ({@code Corpus#getOntologyDiagram}) must never throw. */
    @Test
    void aFailingEndpointYieldsAnEmptyGraphForTheDerivedProperty() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class)))
                .thenThrow(new RuntimeException("boom"));

        assertEquals("{\"nodes\":[],\"links\":[],\"legend\":[]}", corpora.ontologyAsD3Graph("Pet"));
    }

    /**
     * A named-graph endpoint (such as the reference-ontology Fuseki image) has an <b>empty default
     * graph</b>, so every remote query must carry a {@code FROM <graph>} dataset clause for each
     * configured graph — otherwise the search silently returns nothing.
     */
    @Test
    void remoteQueriesAreScopedToTheConfiguredNamedGraphs() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        remote.setDefaultGraphs(List.of("https://schema.org/", "http://BPMNbasedOntology"));
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        corpora.ontologyAsD3Graph("Person");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertTrue(queries.getAllValues().stream()
                        .allMatch(query -> query.contains(
                                "FROM <https://schema.org/> FROM <http://BPMNbasedOntology>")),
                "every remote query must be scoped to the configured graphs: " + queries.getAllValues());
    }

    /** With no {@code default-graphs} the query stays unscoped — correct for a single-default-graph endpoint. */
    @Test
    void remoteQueriesAreUnscopedWhenNoDefaultGraphsAreConfigured() {
        final RdfProperties remote = new RdfProperties();
        remote.setEndpointUrl("https://example.org/sparql");
        final Corpora corpora = corporaWith(remote);

        when(rdfPort.selectRemote(anyString(), anyString(), any(RdfRemoteOptions.class))).thenReturn(List.of());

        corpora.ontologyAsD3Graph("Person");

        final ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(rdfPort, atLeastOnce()).selectRemote(eq("https://example.org/sparql"), queries.capture(),
                any(RdfRemoteOptions.class));
        assertTrue(queries.getAllValues().stream().noneMatch(query -> query.contains("FROM <")),
                "no dataset clause is expected: " + queries.getAllValues());
    }
}
