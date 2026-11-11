package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.rdf.OntologyQueries;
import domox.dom.rdf.RdfGraphDiagrams;
import domox.dom.rdf.RdfProperties;
import domox.dom.rdf.UmlLayoutDiagrams;
import domox.port.RdfGraphTO;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import domox.port.RdfTripleTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The <b>ontology-diagram</b> service: searches the configured RDF/OWL source and draws the result,
 * either as an interactive force-layout graph ({@link OntologyDiagram}) or as one of the UML class
 * diagrams the export stores ({@link UmlDiagram}).
 *
 * <p><b>Why it is its own service.</b>  This is the RDF/ontology axis of the application — it changes
 * when the ontology source, the SPARQL dialect or the diagram contract changes, and for no other
 * reason.  It used to live on {@code Corpora} alongside corpus lifecycle and the requirements
 * pipeline; splitting it leaves each service with one reason to change.  The SPARQL <em>text</em> is
 * further separated into {@link OntologyQueries} (pure, static), so this class only executes queries
 * and shapes view models.</p>
 *
 * <p><b>Local vs. remote.</b>  With a blank {@code domox.rdf.endpoint-url} everything is read from the
 * bundled classpath ontology through {@link RdfPort#select}/{@link RdfPort#construct} (offline and
 * deterministic — the mode the integration tests pin).  With an endpoint configured, the focus query
 * and the triple fetch are issued through {@link RdfPort#selectRemote}, scoped to the configured named
 * graphs.</p>
 */
@Named(DomainModule.NAMESPACE + ".OntologyDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class OntologyDiagrams {

    private static final Logger log = LoggerFactory.getLogger(OntologyDiagrams.class);

    private final RdfPort rdfPort;
    private final RdfGraphDiagrams rdfGraphDiagrams;
    private final UmlLayoutDiagrams umlLayoutDiagrams;
    private final RdfProperties rdfProperties;

    @Inject
    public OntologyDiagrams(
            final RdfPort rdfPort,
            final RdfGraphDiagrams rdfGraphDiagrams,
            final UmlLayoutDiagrams umlLayoutDiagrams,
            final RdfProperties rdfProperties) {
        this.rdfPort = rdfPort;
        this.rdfGraphDiagrams = rdfGraphDiagrams;
        this.umlLayoutDiagrams = umlLayoutDiagrams;
        this.rdfProperties = rdfProperties;
    }

    // ======================================================================
    // Actions
    // ======================================================================

    /**
     * Searches the configured ontology source (the local Fuseki SPARQL endpoint by default; the
     * bundled classpath ontology when no endpoint is set) for {@code searchTerm} and opens the
     * matching neighbourhood as an interactive D3 graph.
     *
     * <p>Returns an {@link OntologyDiagram} view model rather than the raw JSON: {@code @D3Graph}
     * is a property facet, so an action returning a plain {@code String} is displayed as text
     * instead of being drawn as a graph.</p>
     *
     * @param searchTerm text to look for; blank renders the whole ontology
     */
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(
            named = "Search Ontology (D3)",
            describedAs = "Search the RDF/OWL ontology and render the matches as an interactive D3 graph",
            sequence = "5")
    public OntologyDiagram searchOntologyAsD3Graph(final String searchTerm) {
        return ontologyDiagram(searchTerm);
    }

    /**
     * Opens one of the UML class diagrams the ontology export ships, reconstructed from the layout the
     * model itself stores — each box's rectangle and each connector's polyline — rather than laid out
     * afresh.  This is what makes the result look like the model's own diagram.
     *
     * @param diagramName the diagram to draw; blank picks the richest one (see
     *                    {@link #choices0OpenUmlDiagram()})
     */
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(
            named = "Open UML Diagram (D3)",
            describedAs = "Draw one of the UML class diagrams the export stores, at its own coordinates",
            sequence = "6")
    public UmlDiagram openUmlDiagram(
            @Parameter(optionality = Optionality.OPTIONAL)
            @ParameterLayout(
                    named = "Diagram",
                    describedAs = "Which stored diagram to draw; leave blank for the richest one")
            final String diagramName) {
        return umlDiagram(diagramName);
    }

    /** The diagrams the export ships, offered as the action parameter's choices. */
    @MemberSupport
    public List<String> choices0OpenUmlDiagram() {
        try {
            return umlLayoutDiagrams.diagramNames(fullGraph());
        } catch (final RuntimeException ex) {
            log.warn("Could not list UML diagrams from {}: {}", sourceDescription(), rootMessage(ex));
            return List.of();
        }
    }

    // ======================================================================
    // View-model construction
    // ======================================================================

    /**
     * Builds the view model for {@code diagramName}, reporting a failure on the model rather than
     * throwing — an unreachable endpoint, or a graph without that diagram, must not surface as a raw
     * 500/stack trace.
     * <p>
     * Also used by {@link UmlDiagram#getDiagram()} when the picture has to be re-derived — e.g. on
     * reload of a bookmarked view model — so the reason survives too.
     */
    @Programmatic
    public UmlDiagram umlDiagram(final String diagramName) {
        final UmlDiagram viewModel = UmlDiagram.of(diagramName);
        try {
            viewModel.render(umlLayoutDiagrams.renderLayout(fullGraph(), diagramName));
        } catch (final RuntimeException ex) {
            final String reason = rootMessage(ex);
            log.warn("UML diagram '{}' could not be reconstructed from {}: {}",
                    diagramName, sourceDescription(), reason);
            viewModel.fail("Could not read " + sourceDescription() + ": " + reason);
        }
        return viewModel;
    }

    /**
     * Builds the view model for {@code searchTerm}, reporting a query failure on the model rather
     * than throwing (a failing endpoint must not surface as a raw 500/stack trace).
     * <p>
     * Also used by {@link OntologyDiagram#getDiagram()} when the graph has to be re-derived — e.g.
     * after an in-place edit or on reload of a bookmarked view model — so the reason survives too.
     */
    @Programmatic
    public OntologyDiagram ontologyDiagram(final String searchTerm) {
        final OntologyDiagram viewModel = OntologyDiagram.of(searchTerm);
        try {
            viewModel.render(ontologyGraphJson(searchTerm));
        } catch (final RuntimeException ex) {
            final String reason = rootMessage(ex);
            log.warn("Ontology search for '{}' failed against {}: {}", searchTerm, sourceDescription(), reason);
            viewModel.fail("Could not query " + sourceDescription() + ": " + reason);
        }
        return viewModel;
    }

    /**
     * Renders the <em>whole</em> configured RDF/OWL ontology as a D3 graph.
     *
     * <p>The parameterless entry point used by derived (transient) properties such as
     * {@code Corpus#getOntologyDiagram()}: a property cannot take a search term, so it always
     * renders everything.  Marked {@link Programmatic} so it is not itself exposed as an action.</p>
     */
    @Programmatic
    public String ontologyAsD3Graph() {
        return ontologyAsD3Graph("");
    }

    /**
     * The D3 graph JSON for {@code searchTerm} — the actual SPARQL work behind
     * {@link #searchOntologyAsD3Graph(String)} and {@link OntologyDiagram#getDiagram()}.
     *
     * <p>Two SPARQL queries are issued through {@link RdfPort}: a {@code SELECT} that finds the
     * <i>focus</i> resources — IRIs whose own IRI or label names the term — and a query that returns
     * those resources' own triples plus every triple pointing at them.  That one hop of context means
     * the D3 graph shows structure rather than isolated nodes.</p>
     *
     * @param searchTerm text to look for; blank renders the whole ontology
     * @return D3 graph JSON ({@code {nodes,links,legend}}) as consumed by the client renderer
     */
    @Programmatic
    public String ontologyAsD3Graph(final String searchTerm) {
        try {
            return ontologyGraphJson(searchTerm);
        } catch (final RuntimeException ex) {
            // Never throw from a derived property: log and render an empty graph instead.
            log.warn("Ontology diagram for '{}' could not be built from {}: {}",
                    searchTerm, sourceDescription(), rootMessage(ex));
            return rdfGraphDiagrams.renderDiagram(new RdfGraphTO(List.of(), Map.of()));
        }
    }

    /**
     * The graph JSON, <em>throwing</em> if the source cannot be queried — so
     * {@link #searchOntologyAsD3Graph(String)} can report the reason instead of showing a bare
     * stack trace.
     */
    private String ontologyGraphJson(final String searchTerm) {
        final List<String> focus = focusIris(searchTerm);
        final RdfGraphTO graph;
        if (isBlank(searchTerm)) {
            graph = fetchGraph(OntologyQueries.ALL_TRIPLES_QUERY, allTriplesSelectQuery());
        } else if (focus.isEmpty()) {
            // no match: render an empty (but well-formed) graph rather than dumping the whole ontology
            return rdfGraphDiagrams.renderDiagram(new RdfGraphTO(List.of(), Map.of()));
        } else {
            graph = fetchGraph(OntologyQueries.neighbourhoodQuery(focus),
                    OntologyQueries.neighbourhoodSelectQuery(focus, queryLimit()));
        }
        return rdfGraphDiagrams.renderDiagram(graph);
    }

    // ======================================================================
    // Query execution
    // ======================================================================

    /**
     * Fetches one of the two graph shapes: the local {@code CONSTRUCT} against the bundled classpath
     * ontology, or — when {@link RdfProperties#isRemote() an endpoint is configured} — the equivalent
     * {@code SELECT} issued through {@link RdfPort#selectRemote}.
     *
     * @param localConstruct the {@code CONSTRUCT} used in local mode
     * @param remoteSelect   the {@code SELECT} used in remote mode
     */
    private RdfGraphTO fetchGraph(final String localConstruct, final String remoteSelect) {
        return isRemote()
                ? triplesFromRemote(rdfProperties.getEndpointUrl(), remoteSelect)
                : rdfPort.construct(localResource(), localConstruct);
    }

    /**
     * The <em>whole</em> configured graph, with no row cap: an export's layout resources are most of
     * its triples, so a truncated fetch would silently reconstruct a partial picture.  Used for the
     * UML diagrams, which need the stored geometry in full.
     */
    private RdfGraphTO fullGraph() {
        return isRemote()
                ? triplesFromRemote(rdfProperties.getEndpointUrl(), OntologyQueries.allTriplesSelectQuery(0))
                : rdfPort.construct(localResource(), OntologyQueries.ALL_TRIPLES_QUERY);
    }

    /** Whether the graph is sourced from a configured SPARQL endpoint rather than the fixture. */
    private boolean isRemote() {
        return rdfProperties.isRemote();
    }

    /**
     * Resolves the search term to the IRIs it names.
     *
     * <p>Two passes: the <b>exact</b> query first, and only if that finds nothing the
     * <b>word-boundary</b> query.  Exact-first keeps {@code Pet} from dragging in {@code Petit}; the
     * fallback keeps a partial phrase such as {@code Golden Retriever} usable.</p>
     *
     * <p>{@code isIRI} keeps literals out — a literal cannot be written as a subject in the
     * follow-up query's {@code VALUES} clause — and blank nodes ({@code _:x}, which likewise cannot
     * be written as {@code <...>}) are dropped here.</p>
     */
    private List<String> focusIris(final String searchTerm) {
        if (isBlank(searchTerm)) {
            return List.of();
        }
        final String language = rdfProperties.getLabelLanguage();
        final List<String> exact = runFocusQuery(OntologyQueries.focusQuery(searchTerm, language));
        return exact.isEmpty()
                ? runFocusQuery(OntologyQueries.wordMatchQuery(searchTerm, language))
                : exact;
    }

    /** Runs one focus query and reduces it to the distinct, usable focus IRIs. */
    private List<String> runFocusQuery(final String sparql) {
        return runSelect(sparql + limitClause()).stream()
                .map(row -> row.get("focus"))
                .filter(Objects::nonNull)
                .filter(iri -> !iri.startsWith("_:"))
                .distinct()
                .toList();
    }

    /** Runs a {@code SELECT} against the configured endpoint, or against the bundled classpath ontology. */
    private List<Map<String, String>> runSelect(final String sparql) {
        return isRemote()
                ? rdfPort.selectRemote(rdfProperties.getEndpointUrl(),
                        OntologyQueries.withDataset(sparql, rdfProperties.getDefaultGraphs()), remoteOptions())
                : rdfPort.select(localResource(), sparql);
    }

    /**
     * Builds a graph from a remote {@code SELECT ?g ?s ?p ?o (isLiteral(?o) AS ?lit)}.
     *
     * <p><b>Why a {@code SELECT} rather than a {@code CONSTRUCT}.</b>  {@link RdfPort#construct}
     * reads a <em>local</em> graph while {@link RdfPort#selectRemote} is the endpoint API, so the
     * remote path asks for the triples as rows and rebuilds {@link RdfTripleTO}s from them.  The
     * {@code isLiteral(?o)} projection carries the resource/literal distinction that a bare
     * {@code ?o} binding would lose — {@code selectRemote} flattens every binding to its lexical
     * form.</p>
     *
     * <p>The {@code ?g} projection records which named graph each IRI was read from, so the diagram
     * can label a node with its source ontology (gist, BFO, …).  A resource that only ever appears as
     * an object is attributed to the graph of the statement that mentioned it.</p>
     */
    private RdfGraphTO triplesFromRemote(final String endpointUrl, final String sparql) {
        final List<RdfTripleTO> triples = new ArrayList<>();
        final Map<String, String> graphByIri = new LinkedHashMap<>();
        for (final Map<String, String> row : rdfPort.selectRemote(endpointUrl,
                OntologyQueries.withNamedDataset(sparql, rdfProperties.getDefaultGraphs()), remoteOptions())) {
            final String subject = row.get("s");
            final String predicate = row.get("p");
            final String objectValue = row.get("o");
            if (subject == null || predicate == null || objectValue == null) {
                continue;
            }
            final boolean literal = "true".equals(row.get("lit"));
            triples.add(new RdfTripleTO(subject, predicate, objectValue, literal));
            final String graph = row.get("g");
            if (graph != null) {
                graphByIri.putIfAbsent(subject, graph);
                if (!literal) {
                    graphByIri.putIfAbsent(objectValue, graph);
                }
            }
        }
        return new RdfGraphTO(triples, Map.of(), graphByIri);
    }

    /** {@code LIMIT} clause for remote queries only — the bundled fixture is tiny. */
    private String limitClause() {
        return queryLimit() > 0 ? "\nLIMIT " + queryLimit() : "";
    }

    /** The configured row cap, or {@code 0} (no cap) in local mode. */
    private int queryLimit() {
        return isRemote() ? Math.max(1, rdfProperties.getQueryLimit()) : 0;
    }

    /** The whole-ontology {@code SELECT}, with the configured row cap. */
    private String allTriplesSelectQuery() {
        return OntologyQueries.allTriplesSelectQuery(queryLimit());
    }

    /** Per-call remote options: the configured timeout and {@code User-Agent}. */
    private RdfRemoteOptions remoteOptions() {
        return new RdfRemoteOptions(
                Duration.ofSeconds(Math.max(1, rdfProperties.getQueryTimeoutSeconds())),
                rdfProperties.getUserAgent());
    }

    /**
     * Where the local (non-endpoint) graph is read from: the configured
     * {@link RdfProperties#getLocalResource() local-resource} — a classpath resource name or a
     * filesystem path — or the bundled default when that is blank.
     */
    private String localResource() {
        final String configured = rdfProperties.getLocalResource();
        return configured == null || configured.isBlank()
                ? RdfProperties.DEFAULT_ONTOLOGY_RESOURCE
                : configured.trim();
    }

    /** What the graph is being read from — for log messages. */
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

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
