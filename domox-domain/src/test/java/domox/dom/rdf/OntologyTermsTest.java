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
    private static final String OWL_EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass";

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
    void aBlankNodeDefinitionIsNotReportedAsASynonymOrSuperClass() {
        // gist:Contract defines itself with owl:equivalentClass pointing at an owl:Restriction BLANK
        // node, and its rdfs:subClassOf may point at one too.  Both are anonymous, so neither is
        // evidence — before the guard, the reviewer was shown the node's "_:" identifier as a synonym.
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("SELECT DISTINCT ?focus")))
                .thenReturn(List.of(Map.of("focus", "http://x/Contract")));
        when(rdfPort.select(eq(RdfProperties.DEFAULT_ONTOLOGY_RESOURCE), contains("VALUES ?term")))
                .thenReturn(List.of(
                        Map.of("term", "http://x/Contract", "predicate", RDF_TYPE,
                                "value", RDFS_CLASS, "lit", "false"),
                        Map.of("term", "http://x/Contract", "predicate", RDFS_LABEL,
                                "value", "Contract", "lit", "true"),
                        Map.of("term", "http://x/Contract", "predicate", RDFS_SUBCLASS_OF,
                                "value", "_:b0", "lit", "false"),
                        Map.of("term", "http://x/Contract", "predicate", OWL_EQUIVALENT_CLASS,
                                "value", "_:73fda4a67b34702600201258f11e572f", "lit", "false"),
                        Map.of("term", "http://x/Contract", "predicate", OWL_EQUIVALENT_CLASS,
                                "value", "http://other/Agreement", "lit", "false")));

        final OntologyTerm term = termsWith(new RdfProperties()).lookup("Contract").terms().getFirst();

        assertThat(term.synonyms()).as("only the named equivalent class is evidence")
                .containsExactly("Agreement");
        assertThat(term.superClass()).as("a blank-node subClassOf names nothing").isNull();
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
}
