package domox.dom.requirements;

import domox.dom.crc.service.ClassArchetypeClassifier;
import domox.dom.crc.service.DomainModels;
import domox.dom.rdf.RdfGraphDiagrams;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.RuleMatches;
import domox.port.NlpPort;
import domox.port.RdfGraphTO;
import domox.port.RdfPort;
import domox.port.RdfTripleTO;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

    @BeforeEach
    void setUp() {
        classUnderTest = new Corpora(
                repositoryService, factoryService, ruleMatches, documents, List.of(),
                domainModels, archetypeClassifier, candidateResolver, nlpPort,
                rdfPort, new RdfGraphDiagrams());
    }

    @Test
    void searchOntologyAsD3Graph() {
        // given — the port reports one focus IRI and returns that resource's neighbourhood
        when(rdfPort.select(eq(Corpora.ONTOLOGY_RESOURCE), contains("Buddy")))
                .thenReturn(List.of(Map.of("focus", PS + "Buddy")));
        final RdfGraphTO graph = new RdfGraphTO(List.of(
                RdfTripleTO.resource(PS + "Buddy", RDF_TYPE, PS + "Dog"),
                RdfTripleTO.literal(PS + "Buddy", RDFS_LABEL, "Buddy")
        ), Map.of("ps", PS));
        when(rdfPort.construct(eq(Corpora.ONTOLOGY_RESOURCE),
                contains("VALUES ?focus { <" + PS + "Buddy> }")))
                .thenReturn(graph);

        // when
        final String json = classUnderTest.searchOntologyAsD3Graph("Buddy");

        // then — the neighbourhood is rendered as D3 JSON
        assertTrue(json.contains("\"nodes\":["), json);
        assertTrue(json.contains("\"id\":\"" + PS + "Buddy\""), json);
        assertTrue(json.contains("\"source\":\"" + PS + "Buddy\""), json);
        assertTrue(json.contains("\"name\":\"Buddy\""), json);
    }

    @Test
    void searchOntologyWithoutMatchesRendersEmptyGraph() {
        // given — the focus query finds nothing
        when(rdfPort.select(eq(Corpora.ONTOLOGY_RESOURCE), contains("does-not-exist")))
                .thenReturn(List.of());

        // when
        final String json = classUnderTest.searchOntologyAsD3Graph("does-not-exist");

        // then — an empty (but well-formed) graph, not the whole ontology
        assertEquals("{\"nodes\":[],\"links\":[],\"legend\":[]}", json);
    }

    @Test
    void ontologyAsD3GraphRendersTheWholeOntology() {
        // given — the parameterless entry point takes the "all triples" path (no SELECT issued)
        when(rdfPort.construct(eq(Corpora.ONTOLOGY_RESOURCE),
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

    @Test
    void neighbourhoodQueryListsTheFocusIrisInAValuesClause() {
        final String sparql = Corpora.neighbourhoodQuery(List.of("http://x/A", "http://x/B"));

        assertTrue(sparql.contains("CONSTRUCT { ?s ?p ?o }"), sparql);
        assertTrue(sparql.contains("VALUES ?focus { <http://x/A> <http://x/B> }"), sparql);
    }

    @Test
    void escapeSparqlStringNeutralisesQuotesAndBackslashes() {
        assertEquals("a \\\"b\\\"", Corpora.escapeSparqlString("a \"b\""));
        assertEquals("C:\\\\path", Corpora.escapeSparqlString("C:\\path"));
    }
}
