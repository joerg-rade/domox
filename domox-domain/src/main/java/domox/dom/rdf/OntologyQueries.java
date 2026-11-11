package domox.dom.rdf;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the SPARQL text the ontology features issue — the <b>query vocabulary</b> of DoMoX.
 *
 * <p>Every method is a <em>pure function</em>: no state, no port, no configuration.  The execution
 * side (deciding local vs. remote, applying the configured graphs, timeouts and row caps) lives in
 * {@link domox.dom.requirements.OntologyDiagrams} and {@link OntologyTerms}; this class only knows how
 * to spell a query.</p>
 *
 * <p><b>Why it is its own type.</b>  These builders were once spread through {@code Corpora}, which
 * made a domain service responsible for SPARQL syntax as well as for corpus lifecycle.  They are also
 * shared: {@link OntologyTerms} reuses the focus queries verbatim so that the evidence a reviewer sees
 * matches exactly what the ontology diagram draws.</p>
 *
 * <p><b>Dataset scoping.</b>  A named-graph endpoint has an <em>empty</em> default graph, so a query
 * must be scoped or it matches nothing.  {@link #withDataset} emits {@code FROM <g>} (the graphs are
 * merged into the default graph — the query body stays graph-agnostic), while
 * {@link #withNamedDataset} emits {@code FROM NAMED <g>}, which is what a query addressing
 * {@code GRAPH ?g} needs in order to report <em>which</em> ontology a row came from.</p>
 */
public final class OntologyQueries {

    private OntologyQueries() {
    }

    /** CONSTRUCT returning every statement — the "no filter" case (blank search term). */
    public static final String ALL_TRIPLES_QUERY = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    // ------------------------------------------------------------------
    // graph fetch
    // ------------------------------------------------------------------

    /**
     * Builds the {@code CONSTRUCT} that returns the focus resources' own triples plus every triple
     * pointing at them — one hop of context around each match (local mode).
     */
    public static String neighbourhoodQuery(final List<String> focusIris) {
        return "CONSTRUCT { ?s ?p ?o }\nWHERE {\n" + neighbourhoodPattern(focusIris) + "\n}";
    }

    /**
     * {@code SELECT} form of {@link #ALL_TRIPLES_QUERY}, for a remote endpoint.
     *
     * <p>Projected with {@code ?g} inside {@code GRAPH ?g { … }} so each row reports the named graph it
     * came from (the diagram labels a node with its source ontology); pair it with
     * {@link #withNamedDataset}.  Pass {@code 0} for no row cap — an export's layout resources are most
     * of its triples, so a truncated fetch would silently reconstruct a partial picture.</p>
     *
     * @param limit the {@code LIMIT} to apply, or {@code 0} for none
     */
    public static String allTriplesSelectQuery(final int limit) {
        return "SELECT ?g ?s ?p ?o (isLiteral(?o) AS ?lit)\nWHERE { GRAPH ?g { ?s ?p ?o } }"
                + limitClause(limit);
    }

    /** {@code SELECT} form of {@link #neighbourhoodQuery}, for a remote endpoint. */
    public static String neighbourhoodSelectQuery(final List<String> focusIris, final int limit) {
        return "SELECT ?g ?s ?p ?o (isLiteral(?o) AS ?lit)\nWHERE {\n  GRAPH ?g {\n"
                + neighbourhoodPattern(focusIris) + "\n  }\n}" + limitClause(limit);
    }

    /**
     * The one-hop pattern shared by {@link #neighbourhoodQuery} and {@link #neighbourhoodSelectQuery}:
     * the focus resources' own triples plus every triple pointing at them.
     *
     * <p><b>The {@code VALUES} clause must sit INSIDE each {@code UNION} branch.</b>  Written once in
     * front of the union it does not constrain the branch patterns: SPARQL evaluates
     * {@code Join(VALUES, Union(A,B))} by evaluating the union <em>independently</em>, so
     * {@code ?focus} is unbound inside the branches — they then match <em>every</em> triple, and the
     * join does not filter them out (an unbound variable is compatible with any binding).  That bug
     * made every search return the whole ontology; the regression is pinned by
     * {@code JenaRdfAdapterTest."search restricts the graph to the matching neighbourhood"}.</p>
     */
    private static String neighbourhoodPattern(final List<String> focusIris) {
        final String values = focusIris.stream()
                .map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(" "));
        return "{ VALUES ?s { %1$s } ?s ?p ?o }\nUNION\n{ VALUES ?o { %1$s } ?s ?p ?o }"
                .formatted(values);
    }

    /** {@code LIMIT} clause; {@code limit <= 0} yields none (the bundled fixture is tiny). */
    private static String limitClause(final int limit) {
        return limit > 0 ? "\nLIMIT " + limit : "";
    }

    // ------------------------------------------------------------------
    // focus (search term → the IRIs it names)
    // ------------------------------------------------------------------

    /** @see #focusQuery(String, String) */
    public static String focusQuery(final String searchTerm) {
        return focusQuery(searchTerm, "");
    }

    /**
     * The focus {@code SELECT}: the resources the search term <b>names</b>.
     *
     * <p><b>Exact, not substring.</b>  The term is compared for <em>equality</em> (case-insensitively)
     * against either the IRI's local name ({@code REPLACE(STR(?focus), "^.*[/#]", "")}) or a label —
     * {@code rdfs:label}, {@code skos:prefLabel} <em>or</em> {@code ontouml:name} (a UML/OntoUML export
     * names its elements with the latter and carries no {@code rdfs:label} at all, so without it a
     * search over such a model could never match anything).  A substring {@code REGEX} — the previous
     * behaviour — made a search for {@code Pet} also return {@code Petit}, {@code PetShow},
     * {@code competition}, … : noise that buries the very concept being asked for.</p>
     *
     * <p>The three branches cover: the resource as a <em>subject</em>, the resource as an
     * <em>object</em> (so vocabulary IRIs such as {@code owl:Class} stay reachable), and
     * <em>label-only</em> matches.</p>
     *
     * @param searchTerm    the concept name to resolve, e.g. {@code "Pet"}
     * @param labelLanguage when non-blank, labels must carry that language tag (or be untagged);
     *                      blank accepts any label
     */
    public static String focusQuery(final String searchTerm, final String labelLanguage) {
        return focusQuery("=", escapeSparqlString(
                searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT)), labelLanguage);
    }

    /**
     * The <b>fallback</b> focus {@code SELECT}, used only when {@link #focusQuery} finds nothing: the
     * same match, but on <b>word boundaries</b> instead of equality.
     *
     * <p>{@code Golden Retriever} then finds the label {@code "Buddy the Golden Retriever"}, while
     * {@code Pet} still does <em>not</em> drag in {@code Petit} — there is no word boundary between
     * "pet" and "it".  The term is wrapped in {@code \Q…\E} so any regex metacharacters in it are
     * matched literally.</p>
     *
     * @see #focusQuery(String, String)
     */
    public static String wordMatchQuery(final String searchTerm, final String labelLanguage) {
        final String term = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        final String regex = "(^|\\W)" + Pattern.quote(term) + "(\\W|$)";
        return focusQuery("REGEX", escapeSparqlString(regex), labelLanguage);
    }

    /**
     * Shared body of {@link #focusQuery} and {@link #wordMatchQuery}.
     *
     * @param comparison {@code "="} for equality, {@code "REGEX"} for the word-boundary fallback
     * @param value      already lower-cased and SPARQL-escaped (an equality value or a regex)
     */
    private static String focusQuery(
            final String comparison, final String value, final String labelLanguage) {
        // NB: equality is INFIX in SPARQL ("expr = value"); only REGEX is a call — "=(expr, value)"
        // is not valid SPARQL and makes the whole query fail to parse.
        final String localName = comparisonExpr(
                comparison, "LCASE(REPLACE(STR(?focus), \"^.*[/#]\", \"\"))", value);
        final String label = comparisonExpr(comparison, "LCASE(STR(?l))", value);
        final String language = labelLanguage == null ? "" : labelLanguage.trim();
        final String langFilter = language.isEmpty()
                ? ""
                : " && (LANG(?l) = \"%s\" || LANG(?l) = \"\")".formatted(escapeSparqlString(language));
        return """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
                PREFIX ontouml: <https://w3id.org/ontouml#>
                SELECT DISTINCT ?focus WHERE {
                  { ?focus ?p ?o . FILTER(isIRI(?focus) && %1$s) }
                  UNION
                  { ?focus ?labelPred ?l .
                    FILTER( (?labelPred = rdfs:label || ?labelPred = skos:prefLabel
                             || ?labelPred = ontouml:name)
                            && isIRI(?focus) && %2$s%3$s ) }
                  UNION
                  { ?s ?p ?focus . FILTER(isIRI(?focus) && %1$s) }
                }
                """.formatted(localName, label, langFilter);
    }

    /** {@code lhs = "value"} for equality, {@code REGEX(lhs, "value")} for the word-boundary match. */
    private static String comparisonExpr(
            final String comparison, final String lhs, final String value) {
        return "=".equals(comparison)
                ? lhs + " = \"" + value + "\""
                : "REGEX(" + lhs + ", \"" + value + "\")";
    }

    // ------------------------------------------------------------------
    // dataset scoping
    // ------------------------------------------------------------------

    /**
     * Scopes a query to {@code graphs} with SPARQL {@code FROM <graph>} dataset clauses — the graphs
     * are merged into the default graph, so the query body stays graph-agnostic.
     */
    public static String withDataset(final String sparql, final List<String> graphs) {
        return withDatasetClauses(sparql, graphs, false);
    }

    /**
     * Scopes a query to {@code graphs} with {@code FROM NAMED <graph>} clauses instead, so a query can
     * address each graph <em>by name</em> through {@code GRAPH ?g { … }} and report which one a row
     * came from.
     */
    public static String withNamedDataset(final String sparql, final List<String> graphs) {
        return withDatasetClauses(sparql, graphs, true);
    }

    /** Shared body of {@link #withDataset} / {@link #withNamedDataset}. */
    private static String withDatasetClauses(
            final String sparql, final List<String> graphs, final boolean named) {
        if (graphs == null || graphs.isEmpty()) {
            return sparql;
        }
        final String keyword = named ? "FROM NAMED <" : "FROM <";
        final String clause = graphs.stream()
                .filter(graph -> graph != null && !graph.isBlank())
                .map(graph -> keyword + graph.trim() + "> ")
                .collect(Collectors.joining());
        if (clause.isEmpty()) {
            return sparql;
        }
        // Dataset clauses sit between the projection/template and WHERE.  Every query built here has a
        // single top-level WHERE, and the only string literals (the lower-cased search term) can never
        // contain the upper-case keyword.
        final int where = sparql.indexOf("WHERE");
        return where < 0 ? sparql : sparql.substring(0, where) + clause + sparql.substring(where);
    }

    // ------------------------------------------------------------------
    // escaping
    // ------------------------------------------------------------------

    /** Escapes a value for use inside a double-quoted SPARQL string literal. */
    public static String escapeSparqlString(final String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
