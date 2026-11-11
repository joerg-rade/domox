package domox.dom.rdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the SPARQL <em>text</em> the ontology features issue — the pure builders in
 * {@link OntologyQueries}.
 *
 * <p>Split out of the former {@code CorporaTest}: the queries are shared (the ontology diagram and
 * {@link OntologyTerms} both use the focus queries), so they are pinned independently of whoever
 * executes them.</p>
 */
class OntologyQueriesTest {

    // ------------------------------------------------------------------
    // graph fetch
    // ------------------------------------------------------------------

    @Test
    void neighbourhoodQueryRestrictsEachUnionBranchWithValues() {
        final String sparql = OntologyQueries.neighbourhoodQuery(List.of("http://x/A", "http://x/B"));

        assertThat(sparql).contains("CONSTRUCT { ?s ?p ?o }");
        // the VALUES must be INSIDE each branch — in front of the union it binds nothing there
        assertThat(sparql).contains("{ VALUES ?s { <http://x/A> <http://x/B> } ?s ?p ?o }");
        assertThat(sparql).contains("{ VALUES ?o { <http://x/A> <http://x/B> } ?s ?p ?o }");
    }

    /**
     * The remote whole-graph query must project {@code ?g} inside {@code GRAPH ?g { … }} — that is what
     * tells the diagram which ontology a node came from.  A query that forgot the {@code GRAPH ?g}
     * wrapper while being scoped with {@code FROM NAMED} would match only the empty default graph and
     * silently return nothing.
     */
    @Test
    void allTriplesSelectQueryProjectsTheSourceGraph() {
        final String sparql = OntologyQueries.allTriplesSelectQuery(0);

        assertThat(sparql).contains("SELECT ?g ?s ?p ?o (isLiteral(?o) AS ?lit)");
        assertThat(sparql).contains("GRAPH ?g { ?s ?p ?o }");
    }

    /** A positive {@code limit} caps the rows; {@code 0} leaves the query uncapped. */
    @Test
    void allTriplesSelectQueryAppliesTheLimitOnlyWhenPositive() {
        assertThat(OntologyQueries.allTriplesSelectQuery(42)).contains("LIMIT 42");
        assertThat(OntologyQueries.allTriplesSelectQuery(0)).doesNotContain("LIMIT");
    }

    @Test
    void neighbourhoodSelectQueryWrapsThePatternInTheSourceGraph() {
        final String sparql = OntologyQueries.neighbourhoodSelectQuery(List.of("http://x/A"), 7);

        assertThat(sparql).contains("GRAPH ?g {");
        assertThat(sparql).contains("{ VALUES ?s { <http://x/A> } ?s ?p ?o }");
        assertThat(sparql).contains("LIMIT 7");
    }

    // ------------------------------------------------------------------
    // dataset scoping
    // ------------------------------------------------------------------

    @Test
    void withDatasetEmitsFromClauses() {
        final String sparql = OntologyQueries.withDataset(
                "SELECT ?s WHERE { ?s ?p ?o }", List.of("https://schema.org/", "http://BPMNbasedOntology"));

        assertThat(sparql).contains("FROM <https://schema.org/> FROM <http://BPMNbasedOntology>");
        assertThat(sparql).doesNotContain("FROM NAMED");
    }

    @Test
    void withNamedDatasetEmitsFromNamedClauses() {
        final String sparql = OntologyQueries.withNamedDataset(
                "SELECT ?g ?s WHERE { GRAPH ?g { ?s ?p ?o } }", List.of("https://schema.org/"));

        assertThat(sparql).contains("FROM NAMED <https://schema.org/>");
    }

    /** No configured graphs → the query is left unscoped (correct for a single-default-graph endpoint). */
    @Test
    void datasetScopingIsANoOpWithoutGraphs() {
        final String query = "SELECT ?s WHERE { ?s ?p ?o }";

        assertThat(OntologyQueries.withDataset(query, List.of())).isEqualTo(query);
        assertThat(OntologyQueries.withNamedDataset(query, null)).isEqualTo(query);
    }

    // ------------------------------------------------------------------
    // focus
    // ------------------------------------------------------------------

    /**
     * The focus query must compare for <b>equality</b>: a substring {@code REGEX} is what made a
     * search for {@code Pet} also return {@code Petit}, {@code PetShow}, {@code competition}, …
     */
    @Test
    void focusQueryMatchesTheConceptExactlyNotBySubstring() {
        final String sparql = OntologyQueries.focusQuery("Pet");

        assertThat(sparql).contains("SELECT DISTINCT ?focus");
        assertThat(sparql).contains("LCASE(REPLACE(STR(?focus), \"^.*[/#]\", \"\")) = \"pet\"");
        assertThat(sparql).contains("LCASE(STR(?l)) = \"pet\"");
        assertThat(sparql).as("the exact query must not use substring matching").doesNotContain("REGEX(");
    }

    /** Labels may come from {@code skos:prefLabel} as well as {@code rdfs:label}. */
    @Test
    void focusQueryConsidersSkosPrefLabels() {
        final String sparql = OntologyQueries.focusQuery("Pet");

        assertThat(sparql).contains("?labelPred = rdfs:label || ?labelPred = skos:prefLabel");
    }

    /**
     * A UML/OntoUML export names its elements with {@code ontouml:name} and carries no
     * {@code rdfs:label} at all, so the focus query must consider that predicate too — otherwise
     * every search over such a model resolves nothing and renders an empty diagram.
     *
     * @see RdfGraphDiagrams for the matching node-classification rules
     */
    @Test
    void focusQueryConsidersOntoumlNames() {
        final String sparql = OntologyQueries.focusQuery("Commitment");

        assertThat(sparql).contains("PREFIX ontouml: <https://w3id.org/ontouml#>");
        assertThat(sparql).contains("?labelPred = ontouml:name");
    }

    /** A configured label language restricts label matching to that tag (or untagged labels). */
    @Test
    void focusQueryAppliesTheConfiguredLabelLanguage() {
        assertThat(OntologyQueries.focusQuery("Pet", "")).as("no language configured -> no language filter")
                .doesNotContain("LANG(?l)");

        final String english = OntologyQueries.focusQuery("Pet", "en");
        assertThat(english).contains("LANG(?l) = \"en\" || LANG(?l) = \"\"");
    }

    /**
     * The fallback is a <b>word-boundary</b> match, so a partial phrase still resolves while
     * {@code Pet} cannot reach {@code Petit} (no boundary between "pet" and "it").
     */
    @Test
    void wordMatchQueryMatchesOnWordBoundaries() {
        final String sparql = OntologyQueries.wordMatchQuery("Golden Retriever", "");

        assertThat(sparql).contains("REGEX(");
        assertThat(sparql).as("must anchor on a leading boundary").contains("(^|");
        assertThat(sparql).as("must anchor on a trailing boundary").contains("|$)");
        assertThat(sparql).as("the term must be quoted literally").contains("\\Q");
    }

    @Test
    void escapeSparqlStringNeutralisesQuotesAndBackslashes() {
        assertThat(OntologyQueries.escapeSparqlString("a \"b\"")).isEqualTo("a \\\"b\\\"");
        assertThat(OntologyQueries.escapeSparqlString("C:\\path")).isEqualTo("C:\\\\path");
    }
}
