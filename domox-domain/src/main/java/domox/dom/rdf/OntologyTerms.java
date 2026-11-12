package domox.dom.rdf;

import domox.DomainModule;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Looks a term up in the configured RDF/OWL graph and reports what the ontology says about it.
 *
 * <p>This is the <em>evidence</em> behind ontology-assisted candidate review: given a candidate name
 * (e.g. {@code Pet}), it answers "does a reference vocabulary know this term, and is it a class, a
 * property or an instance — and what does it say it is?".  The MCP reviewer tools build on it.</p>
 *
 * <p><b>Two-step query, like the diagram.</b>  A <em>focus</em> {@code SELECT} resolves the term to
 * IRIs — reusing {@link OntologyQueries#focusQuery(String)} / {@link OntologyQueries#wordMatchQuery} verbatim, so the
 * evidence matches exactly what the ontology diagram shows (equality first, word-boundary fallback) —
 * and a second {@code SELECT} fetches those IRIs' labels, comments, super-classes and synonyms.</p>
 *
 * <p><b>Named graphs.</b>  The reference-ontology image serves each vocabulary in its own named graph,
 * so the detail query reads them with {@code GRAPH ?g { … }} — which both spans the graphs and tells
 * us <em>which</em> ontology a match came from (a {@code FROM} clause would merge them and lose that).
 * The focus query is scoped with {@code FROM} instead, because it reuses {@link OntologyQueries}'s
 * graph-agnostic query text.</p>
 *
 * <p><b>Never throws.</b>  An unreachable endpoint yields an empty result plus a message — the caller
 * must be able to tell "the ontology does not know this term" from "the ontology could not be
 * asked".</p>
 */
@Slf4j
@DomainService
@Named(DomainModule.NAMESPACE + ".OntologyTerms")
@Priority(PriorityPrecedence.LATE)
public class OntologyTerms {

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDF_PROPERTY = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
    private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
    private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
    private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";
    private static final String OWL_EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass";
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String SKOS_DEFINITION = "http://www.w3.org/2004/02/skos/core#definition";
    /** A UML/OntoUML export types its classes this way and carries no {@code owl:Class} at all. */
    private static final String ONTOUML_CLASS = "https://w3id.org/ontouml#Class";
    /** OntoUML names its elements with this instead of {@code rdfs:label} (see {@link OntologyQueries}). */
    private static final String ONTOUML_NAME = "https://w3id.org/ontouml#name";

    private static final String PREFIXES = """
            PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl:  <http://www.w3.org/2002/07/owl#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX ontouml: <https://w3id.org/ontouml#>
            """;

    /** The predicates that describe a term — everything else is noise for a reviewer. */
    private static final String DESCRIBED_PREDICATES =
            "rdf:type, rdfs:label, rdfs:comment, rdfs:subClassOf, skos:prefLabel, skos:altLabel, "
                    + "skos:definition, owl:equivalentClass, ontouml:name";

    private final RdfPort rdfPort;
    private final RdfProperties rdfProperties;

    @Inject
    public OntologyTerms(final RdfPort rdfPort, final RdfProperties rdfProperties) {
        this.rdfPort = rdfPort;
        this.rdfProperties = rdfProperties;
    }

    /**
     * Looks {@code term} up in the configured ontology source.
     *
     * @param term the candidate name (or any term) to resolve
     * @return the matches plus the source and, when empty, why — never {@code null}, never throws
     */
    @Programmatic
    public OntologyLookup lookup(final String term) {
        final String source = sourceDescription();
        if (term == null || term.isBlank()) {
            return new OntologyLookup("", source, List.of(), true, "No term given.");
        }
        try {
            final List<String> focus = focusIris(term);
            if (focus.isEmpty()) {
                return new OntologyLookup(term, source, List.of(), true,
                        "No ontology term matches '" + term + "'.");
            }
            return new OntologyLookup(term, source, describe(focus), true, null);
        } catch (final RuntimeException ex) {
            final String reason = rootMessage(ex);
            log.warn("Ontology lookup for '{}' failed against {}: {}", term, source, reason);
            return new OntologyLookup(term, source, List.of(), false,
                    "Could not query " + source + ": " + reason);
        }
    }

    // -- focus ------------------------------------------------------------------

    /**
     * Resolves the term to the IRIs it names: the <b>exact</b> query first, and only if that finds
     * nothing the <b>word-boundary</b> one — the same order the ontology diagram uses, so a term that
     * renders in the diagram also has evidence here.
     */
    private List<String> focusIris(final String term) {
        final String language = rdfProperties.getLabelLanguage();
        final List<String> exact = runFocus(OntologyQueries.focusQuery(term, language));
        return exact.isEmpty()
                ? runFocus(OntologyQueries.wordMatchQuery(term, language))
                : exact;
    }

    private List<String> runFocus(final String sparql) {
        return runSelect(sparql + limitClause()).stream()
                .map(row -> row.get("focus"))
                .filter(Objects::nonNull)
                .filter(iri -> !iri.startsWith("_:"))
                .distinct()
                .toList();
    }

    // -- describe ---------------------------------------------------------------

    /** Fetches the descriptive triples of the focus IRIs and reduces them to one term each. */
    private List<OntologyTerm> describe(final List<String> focus) {
        final List<Map<String, String>> rows = isRemote()
                ? rdfPort.selectRemote(rdfProperties.getEndpointUrl(), describeRemoteQuery(focus), remoteOptions())
                : rdfPort.select(localResource(), describeLocalQuery(focus));
        return reduce(rows);
    }

    /**
     * Remote form: {@code GRAPH ?g} so the source vocabulary is reported with each match.  No
     * {@code LIMIT} — the {@code VALUES} clause already bounds the result to the focus IRIs.
     */
    static String describeRemoteQuery(final List<String> focus) {
        return PREFIXES + """
                SELECT ?g ?term ?predicate ?value (isLiteral(?value) AS ?lit) WHERE {
                  GRAPH ?g {
                    VALUES ?term { %s }
                    ?term ?predicate ?value .
                    FILTER(?predicate IN (%s))
                  }
                }
                """.formatted(values(focus), DESCRIBED_PREDICATES);
    }

    /** Local form: the bundled ontology is a single unnamed graph, so there is no {@code GRAPH ?g}. */
    static String describeLocalQuery(final List<String> focus) {
        return PREFIXES + """
                SELECT ?term ?predicate ?value (isLiteral(?value) AS ?lit) WHERE {
                  VALUES ?term { %s }
                  ?term ?predicate ?value .
                  FILTER(?predicate IN (%s))
                }
                """.formatted(values(focus), DESCRIBED_PREDICATES);
    }

    /** {@code <iri> <iri> …}, dropping anything that could break out of the {@code VALUES} block. */
    private static String values(final List<String> iris) {
        return iris.stream()
                .filter(OntologyTerms::isSafeIri)
                .map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(" "));
    }

    private static boolean isSafeIri(final String iri) {
        return iri != null && !iri.isBlank()
                && iri.indexOf('<') < 0 && iri.indexOf('>') < 0
                && iri.indexOf('"') < 0 && iri.indexOf('{') < 0 && iri.indexOf('}') < 0
                && iri.chars().noneMatch(Character::isWhitespace);
    }

    // -- reduction --------------------------------------------------------------

    /** Folds the descriptive rows (one per triple) into one {@link OntologyTerm} per resource. */
    private static List<OntologyTerm> reduce(final List<Map<String, String>> rows) {
        final Map<String, TermBuilder> byTerm = new LinkedHashMap<>();
        for (final Map<String, String> row : rows) {
            final String term = row.get("term");
            final String predicate = row.get("predicate");
            final String value = row.get("value");
            if (term == null || predicate == null || value == null) {
                continue;
            }
            final TermBuilder builder = byTerm.computeIfAbsent(term, TermBuilder::new);
            if (row.get("g") != null) {
                builder.graph = row.get("g");
            }
            final boolean literal = "true".equals(row.get("lit"));
            switch (predicate) {
                case RDF_TYPE -> {
                    if (!literal) builder.types.add(value);
                }
                case RDFS_LABEL -> builder.label = value;
                case SKOS_PREF_LABEL -> builder.prefLabel = value;
                case ONTOUML_NAME -> {
                    if (builder.prefLabel == null) builder.prefLabel = value;
                }
                case RDFS_COMMENT -> builder.definition = value;
                case SKOS_DEFINITION -> {
                    if (builder.definition == null) builder.definition = value;
                }
                case RDFS_SUBCLASS_OF -> {
                    if (!literal) builder.superClass = value;
                }
                case SKOS_ALT_LABEL -> builder.synonyms.add(value);
                case OWL_EQUIVALENT_CLASS -> {
                    if (!literal) builder.synonyms.add(RdfIriNames.localName(value));
                }
                default -> { /* not one of the descriptive predicates */ }
            }
        }
        return byTerm.values().stream().map(TermBuilder::build).toList();
    }

    /** Accumulates the descriptive triples of one resource. */
    private static final class TermBuilder {

        private final String iri;
        private final List<String> types = new ArrayList<>();
        private final List<String> synonyms = new ArrayList<>();
        private String graph;
        private String label;
        private String prefLabel;
        private String definition;
        private String superClass;

        private TermBuilder(final String iri) {
            this.iri = iri;
        }

        private OntologyTerm build() {
            return new OntologyTerm(
                    graph,
                    iri,
                    label != null ? label : (prefLabel != null ? prefLabel : RdfIriNames.localName(iri)),
                    kindOf(types),
                    superClass != null ? RdfIriNames.localName(superClass) : null,
                    definition,
                    synonyms);
        }

        private static OntologyTerm.Kind kindOf(final List<String> types) {
            if (types.contains(OWL_CLASS) || types.contains(RDFS_CLASS) || types.contains(ONTOUML_CLASS)) {
                return OntologyTerm.Kind.CLASS;
            }
            if (types.contains(OWL_OBJECT_PROPERTY) || types.contains(OWL_DATATYPE_PROPERTY)
                    || types.contains(RDF_PROPERTY)) {
                return OntologyTerm.Kind.PROPERTY;
            }
            return OntologyTerm.Kind.INDIVIDUAL;
        }
    }

    // -- source / query plumbing ------------------------------------------------

    /**
     * Scopes an endpoint query to the configured named graphs with {@code FROM <graph>} clauses.
     *
     * <p>Needed for the <em>focus</em> query only, which reuses {@link OntologyQueries}'s graph-agnostic text:
     * a dataset that serves its data as named graphs has an empty default graph, so an unscoped
     * {@code ?focus ?p ?o} would match nothing.</p>
     */
    private String withDataset(final String sparql) {
        final List<String> graphs = rdfProperties.getDefaultGraphs();
        if (graphs == null || graphs.isEmpty()) {
            return sparql;
        }
        final String clause = graphs.stream()
                .filter(graph -> graph != null && !graph.isBlank())
                .map(graph -> "FROM <" + graph.trim() + "> ")
                .collect(Collectors.joining());
        if (clause.isEmpty()) {
            return sparql;
        }
        final int where = sparql.indexOf("WHERE");
        return where < 0 ? sparql : sparql.substring(0, where) + clause + sparql.substring(where);
    }

    private List<Map<String, String>> runSelect(final String sparql) {
        return isRemote()
                ? rdfPort.selectRemote(rdfProperties.getEndpointUrl(), withDataset(sparql), remoteOptions())
                : rdfPort.select(localResource(), sparql);
    }

    private boolean isRemote() {
        return rdfProperties.isRemote();
    }

    private RdfRemoteOptions remoteOptions() {
        return new RdfRemoteOptions(
                Duration.ofSeconds(Math.max(1, rdfProperties.getQueryTimeoutSeconds())),
                rdfProperties.getUserAgent());
    }

    /** {@code LIMIT} for the focus query only — the bundled fixture is tiny. */
    private String limitClause() {
        return isRemote() ? "\nLIMIT " + Math.max(1, rdfProperties.getQueryLimit()) : "";
    }

    private String localResource() {
        final String configured = rdfProperties.getLocalResource();
        return configured == null || configured.isBlank()
                ? RdfProperties.DEFAULT_ONTOLOGY_RESOURCE
                : configured.trim();
    }

    /** What is being queried — for the evidence and for log messages. */
    private String sourceDescription() {
        return isRemote() ? rdfProperties.getEndpointUrl() : localResource();
    }

    /** The deepest message of a failure, for a user-facing note. */
    private static String rootMessage(final Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        final String message = cause.getMessage();
        return message != null && !message.isBlank() ? message : cause.getClass().getSimpleName();
    }
}
