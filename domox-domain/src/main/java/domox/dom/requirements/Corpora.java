package domox.dom.requirements;

import domox.DomainModule;
import domox.util.FileUtil;
import domox.util.TextFilter;
import domox.dom.UcResources;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.service.ClassArchetypeClassifier;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.DomainModels;
import domox.dom.nlp.Sentence;
import domox.dom.rdf.RdfGraphDiagrams;
import domox.dom.rdf.RdfProperties;
import domox.dom.rdf.UmlLayoutDiagrams;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.TypedDependencyRule;
import domox.port.DocumentTO;
import domox.port.NlpPort;
import domox.port.RdfGraphTO;
import domox.port.RdfPort;
import domox.port.RdfRemoteOptions;
import domox.port.RdfTripleTO;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Named(DomainModule.NAMESPACE + ".Corpora")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class Corpora {

    private static final Logger log = LoggerFactory.getLogger(Corpora.class);

    /**
     * Title of the single Corpus that groups all UC* use-case documents.
     */
    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final RuleMatches ruleMatches;
    private final Documents documents;
    private final List<TypedDependencyRule> rules;
    private final DomainModels domainModels;
    private final ClassArchetypeClassifier archetypeClassifier;
    private final CandidateResolver candidateResolver;
    private final NlpPort nlpPort;
    private final RdfPort rdfPort;
    private final RdfGraphDiagrams rdfGraphDiagrams;
    private final UmlLayoutDiagrams umlLayoutDiagrams;
    private final RdfProperties rdfProperties;

    @Inject
    public Corpora(RepositoryService repositoryService,
                   FactoryService factoryService,
                   RuleMatches ruleMatches,
                   Documents documents,
                   List<TypedDependencyRule> rules,
                   DomainModels domainModels,
                   ClassArchetypeClassifier archetypeClassifier,
                   CandidateResolver candidateResolver,
                   NlpPort nlpPort,
                   RdfPort rdfPort,
                   RdfGraphDiagrams rdfGraphDiagrams,
                   UmlLayoutDiagrams umlLayoutDiagrams,
                   RdfProperties rdfProperties) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.ruleMatches = ruleMatches;
        this.documents = documents;
        this.rules = rules;
        this.domainModels = domainModels;
        this.archetypeClassifier = archetypeClassifier;
        this.candidateResolver = candidateResolver;
        this.nlpPort = nlpPort;
        this.rdfPort = rdfPort;
        this.rdfGraphDiagrams = rdfGraphDiagrams;
        this.umlLayoutDiagrams = umlLayoutDiagrams;
        this.rdfProperties = rdfProperties;
    }

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @ActionLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Corpus create(String title) {
        final Corpus obj = factoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        obj.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> findByTitle(final String title) {
        List<Corpus> answer = new ArrayList<>();
        for (Corpus o : listAll()) {
            if (o.getTitle().equals(title)) {
                answer.add(o);
            }
        }
        return answer;
    }

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "trash")
    public void deleteCorpus(
            @ParameterLayout(named = "Corpus") final Corpus corpus) {
        // Every document of a corpus points at its shared DomainModel via the
        // domain_model_id FK, so removing the corpus no longer cascade-deletes the
        // candidates on its own.
        //
        // FIRST explicitly remove every candidate owned by the shared model,
        // child-first (associations -> actions/properties -> classes).  The orphan
        // rows (e.g. ResponsibilityCdd with classCdd = null) live only in the model's own
        // collections; relying solely on the model's @OneToMany(cascade = ALL) to
        // cascade-delete those lazy collections is unreliable — EclipseLink can
        // issue the DELETE on the DomainModel before the still-referencing children
        // are gone, tripping the DB FK constraints
        // (e.g. FK_ResponsibilityCdd_DOMAINMODEL_ID).  Registering each candidate for
        // removal explicitly — in child-before-parent order — guarantees the FK
        // constraints cannot fire.
        final DomainModel domainModel = corpus.getDomainModel();
        if (domainModel != null) {
            removeCandidates(domainModel.getCollaboratorList());
            removeCandidates(domainModel.getResponsibilityList());
            removeCandidates(domainModel.getAttributeList());
            removeCandidates(domainModel.getClassList());
            corpus.setDomainModel(null);
        }
        // Then drop this corpus's documents (releasing their FK into the shared
        // model), remove the now-empty corpus, and finally purge any remaining
        // rule-match records.
        for (final Document document : corpus.getDocuments()) {
            repositoryService.remove(document);
        }
        repositoryService.remove(corpus);
        ruleMatches.deleteAll();
    }

    /**
     * Registers every element of {@code candidates} for removal with the
     * {@link RepositoryService}, snapshotting the list first so the iteration is
     * unaffected by EclipseLink clearing managed collections during the delete.
     */
    private void removeCandidates(final List<? extends Candidate> candidates) {
        for (final Candidate candidate : new ArrayList<>(candidates)) {
            repositoryService.remove(candidate);
        }
    }

    /**
     * Selectable corpora offered as choices for {@link #deleteCorpus(Corpus)}.
     */
    @MemberSupport
    public List<Corpus> choices0DeleteCorpus() {
        return repositoryService.allInstances(Corpus.class);
    }

    // ======================================================================
    // RDF/OWL ontology → D3 graph
    // ======================================================================

    /* Classpath location of the ontology bundled in {@code domox-domain}'s resources. */
    /**
     * Default graph source <em>while no endpoint is configured</em> (blank
     * {@code domox.rdf.endpoint-url}): the OntoUML {@code services2015} model shipped in
     * {@code domox-domain}'s resources.  Overridable per deployment with
     * {@code domox.rdf.local-resource} (a classpath resource name or a filesystem path) — see
     * {@link #localResource()}.  The running application points at the local Fuseki endpoint, so this
     * resource is the offline / deterministic fallback (and what the integration tests pin).
     */
    public static final String DEFAULT_ONTOLOGY_RESOURCE = "ontology.ttl";

    /** CONSTRUCT returning every statement — the "no filter" case (blank search term). */
    private static final String ALL_TRIPLES_QUERY = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    /**
     * {@code SELECT} form of {@link #ALL_TRIPLES_QUERY} for the endpoint, deliberately <b>without</b> a
     * {@code LIMIT}: an export's layout resources are most of its triples, so a truncated fetch would
     * silently reconstruct a partial picture.  Meant for a locally served UML model.
     */
    private static final String ALL_TRIPLES_SELECT_QUERY =
            "SELECT ?s ?p ?o (isLiteral(?o) AS ?lit)\nWHERE { ?s ?p ?o }";

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
     * The <em>whole</em> configured graph, with no row cap — see {@link #ALL_TRIPLES_SELECT_QUERY} for
     * why the usual {@code query-limit} must not apply here.
     */
    private RdfGraphTO fullGraph() {
        return isRemote()
                ? triplesFromRemote(rdfProperties.getEndpointUrl(), ALL_TRIPLES_SELECT_QUERY)
                : rdfPort.construct(localResource(), ALL_TRIPLES_QUERY);
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
     * <i>focus</i> resources — IRIs whose own IRI or {@code rdfs:label} contains the term,
     * case-insensitively — and a {@code CONSTRUCT} that returns those resources' own triples plus
     * every triple pointing at them.  That one hop of context means the D3 graph shows structure
     * rather than isolated nodes.</p>
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
            graph = fetchGraph(ALL_TRIPLES_QUERY, allTriplesSelectQuery());
        } else if (focus.isEmpty()) {
            // no match: render an empty (but well-formed) graph rather than dumping the whole ontology
            return rdfGraphDiagrams.renderDiagram(new RdfGraphTO(List.of(), Map.of()));
        } else {
            graph = fetchGraph(neighbourhoodQuery(focus), neighbourhoodSelectQuery(focus));
        }
        return rdfGraphDiagrams.renderDiagram(graph);
    }

    /**
     * Fetches one of the two graph shapes: the local {@code CONSTRUCT} against the bundled classpath
     * ontology,
     * or — when {@link RdfProperties#isRemote() an endpoint is configured} — the equivalent
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

    /** Whether the graph is sourced from a configured SPARQL endpoint rather than the fixture. */
    private boolean isRemote() {
        return rdfProperties.isRemote();
    }

    /**
     * Resolves the search term to the IRIs it names.
     *
     * <p>Two passes: the <b>exact</b> query first ({@link #focusQuery}), and only if that finds
     * nothing the <b>word-boundary</b> query ({@link #wordMatchQuery}).  Exact-first keeps
     * {@code Pet} from dragging in {@code Petit}; the fallback keeps a partial phrase such as
     * {@code Golden Retriever} usable.</p>
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
        final List<String> exact = runFocusQuery(focusQuery(searchTerm, language));
        return exact.isEmpty()
                ? runFocusQuery(wordMatchQuery(searchTerm, language))
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

    /** @see #focusQuery(String, String) */
    public static String focusQuery(final String searchTerm) {
        return focusQuery(searchTerm, "");
    }

    /**
     * The focus {@code SELECT}: the resources the search term <b>names</b>.
     *
     * <p><b>Exact, not substring.</b>  The term is compared for <em>equality</em>
     * (case-insensitively) against either the IRI's local name
     * ({@code REPLACE(STR(?focus), "^.*[/#]", "")}) or a label — {@code rdfs:label},
     * {@code skos:prefLabel} <em>or</em> {@code ontouml:name} (a UML/OntoUML export names its
     * elements with the latter and carries no {@code rdfs:label} at all, so without it a search over
     * such a model could never match anything).  A substring {@code REGEX} — the previous behaviour — made a search
     * for {@code Pet} also return {@code Petit}, {@code PetShow}, {@code competition}, … : noise
     * that buries the very concept being asked for.  With equality, {@code Pet} resolves to
     * {@code ps:Pet} (local name {@code Pet}, label {@code "Pet"}) and nothing else, so the rendered
     * neighbourhood is the concept's own relations — its {@code rdfs:subClassOf} targets
     * ({@code Pet → Animal}) and everything pointing at it ({@code Owner —owns→ Pet}).</p>
     *
     * <p>The three branches cover: the resource as a <em>subject</em>, the resource as an
     * <em>object</em> (so vocabulary IRIs such as {@code owl:Class} stay reachable), and
     * <em>label-only</em> matches.</p>
     *
     * <p>Public so the adapter module can exercise the real semantics against Jena
     * (see {@code JenaRdfAdapterTest}); it is a pure function and static, so Causeway does not
     * treat it as a metamodel member.</p>
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
     * The <b>fallback</b> focus {@code SELECT}, used only when {@link #focusQuery} finds nothing:
     * the same match, but on <b>word boundaries</b> instead of equality.
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

    /** Runs a {@code SELECT} against the configured endpoint, or against the bundled classpath ontology. */
    private List<Map<String, String>> runSelect(final String sparql) {
        return isRemote()
                ? rdfPort.selectRemote(rdfProperties.getEndpointUrl(), withDataset(sparql), remoteOptions())
                : rdfPort.select(localResource(), sparql);
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
                ? DEFAULT_ONTOLOGY_RESOURCE
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

    /**
     * Scopes an endpoint query to the configured named graphs by inserting SPARQL {@code FROM <graph>}
     * dataset clauses (see {@link RdfProperties#getDefaultGraphs()}).
     *
     * <p><b>Why this is needed.</b>  A Fuseki dataset that serves its data as <em>named graphs</em> (as
     * the reference-ontology image does — Schema.org, BFO, gist, BBO and sBPMN each in its own graph) has an
     * <b>empty default graph</b>: an unscoped {@code ?s ?p ?o} matches nothing, so every search would
     * come back empty.  {@code FROM <g>} re-bases the query's default graph onto the merge of the listed
     * graphs, so the query bodies stay graph-agnostic and work unchanged against a single-graph endpoint
     * too.</p>
     *
     * <p>Applied to remote queries only: the bundled classpath ontology is a single unnamed graph, and a
     * {@code FROM} clause there would make Jena try to dereference the URI.</p>
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
        // Dataset clauses sit between the projection/template and WHERE.  Every query built here has a
        // single top-level WHERE, and the only string literals (the lower-cased search term) can never
        // contain the upper-case keyword.
        final int where = sparql.indexOf("WHERE");
        return where < 0 ? sparql : sparql.substring(0, where) + clause + sparql.substring(where);
    }

    /** {@code LIMIT} clause for remote queries only — the bundled fixture is tiny. */
    private String limitClause() {
        return isRemote() ? "\nLIMIT " + Math.max(1, rdfProperties.getQueryLimit()) : "";
    }

    /** {@code SELECT} form of {@link #ALL_TRIPLES_QUERY}, for a remote endpoint. */
    private String allTriplesSelectQuery() {
        return "SELECT ?s ?p ?o (isLiteral(?o) AS ?lit)\nWHERE { ?s ?p ?o }" + limitClause();
    }

    /** {@code SELECT} form of {@link #neighbourhoodQuery}, for a remote endpoint. */
    private String neighbourhoodSelectQuery(final List<String> focusIris) {
        return "SELECT ?s ?p ?o (isLiteral(?o) AS ?lit)\nWHERE {\n"
                + neighbourhoodPattern(focusIris) + "\n}" + limitClause();
    }

    /**
     * Builds a graph from a remote {@code SELECT ?s ?p ?o (isLiteral(?o) AS ?lit)}.
     *
     * <p><b>Why a {@code SELECT} rather than a {@code CONSTRUCT}.</b>  {@link RdfPort#construct}
     * reads a <em>local</em> graph while {@link RdfPort#selectRemote} is the endpoint API, so the
     * remote path asks for the triples as rows and rebuilds {@link RdfTripleTO}s from them.  The
     * {@code isLiteral(?o)} projection carries the resource/literal distinction that a bare
     * {@code ?o} binding would lose — {@code selectRemote} flattens every binding to its lexical
     * form.</p>
     */
    private RdfGraphTO triplesFromRemote(final String endpointUrl, final String sparql) {
        final List<RdfTripleTO> triples = new ArrayList<>();
        for (final Map<String, String> row : rdfPort.selectRemote(endpointUrl, withDataset(sparql), remoteOptions())) {
            final String subject = row.get("s");
            final String predicate = row.get("p");
            final String objectValue = row.get("o");
            if (subject == null || predicate == null || objectValue == null) {
                continue;
            }
            triples.add(new RdfTripleTO(subject, predicate, objectValue, "true".equals(row.get("lit"))));
        }
        return new RdfGraphTO(triples, Map.of());
    }

    /**
     * Builds the {@code CONSTRUCT} that returns the focus resources' own triples plus every triple
     * pointing at them — one hop of context around each match (local mode).
     */
    static String neighbourhoodQuery(final List<String> focusIris) {
        return "CONSTRUCT { ?s ?p ?o }\nWHERE {\n" + neighbourhoodPattern(focusIris) + "\n}";
    }

    /**
     * The one-hop pattern shared by {@link #neighbourhoodQuery} and
     * {@link #neighbourhoodSelectQuery}: the focus resources' own triples plus every triple pointing
     * at them.
     *
     * <p><b>The {@code VALUES} clause must sit INSIDE each {@code UNION} branch.</b>  Written once
     * in front of the union it does not constrain the branch patterns: SPARQL evaluates
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

    /** Escapes a value for use inside a double-quoted SPARQL string literal. */
    static String escapeSparqlString(final String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    // ======================================================================
    // Sample use-case loading + analysis pipeline
    // ======================================================================

    /**
     * Loads every UC* sample document into the "Pet Shop Use Cases" corpus, running the
     * full analysis pipeline (match → candidate creation → archetype classification →
     * late-binding resolution) unless the content has already been analysed (duplicate
     * guard). This is the programmatic entry point shared by the {@code Documents} menu
     * action and the {@code LoadSampleFileFixture} fixture script.
     *
     * @return the number of new documents actually loaded ({@code 0} when all were duplicates)
     */
    @Programmatic
    public int loadSampleFiles() {
        final Corpus corpus = corpus();
        log.info("Loading sample into corpus '{}' (id {}).", CORPUS_TITLE, corpus.getId());

        int loaded = 0;
        for (final String filename : UcResources.listUcFilenames()) {
            if (loadIntoCorpus(filename, corpus)) {
                loaded++;
            }
        }

        log.info("Loaded {} new UC* documents into corpus '{}'.", loaded, CORPUS_TITLE);
        return loaded;
    }

    /**
     * Loads a single {@code UC*.md} classpath document into the corpus and runs the full
     * analysis pipeline. Idempotent: returns {@code false} when the content was already
     * analysed.
     *
     * @param filename a {@code UC*.md} filename present in the classpath resources
     * @return {@code true} when a new document was loaded; {@code false} when skipped as a duplicate
     */
    @Programmatic
    public boolean loadUcDocument(final String filename) {
        final Corpus corpus = corpus();
        return loadIntoCorpus(filename, corpus);
    }

    /**
     * The single Corpus that groups all UC* use-case documents.
     */
    private Corpus corpus() {
        return findByTitle(CORPUS_TITLE).stream()
                .findFirst()
                .orElseGet(() -> create(CORPUS_TITLE));
    }

    private void analyzeDocument(
            @ParameterLayout(named = "Document") final Document document) {
        log.info("Starting analysis phase for document: {}", document.getTitle());

        // Every document of a corpus shares a single DomainModel, so candidate
        // names de-duplicate across the whole use-case suite instead of being
        // re-created once per document (which previously duplicated the entire
        // candidate set N times for N documents).
        final Corpus corpus = document.getCorpus();
        DomainModel domainModel = corpus != null ? corpus.getDomainModel() : null;
        if (domainModel == null) {
            domainModel = domainModels.create();
            if (corpus != null) {
                corpus.setDomainModel(domainModel);
            }
        }
        document.setDomainModel(domainModel);

        // Apply each TypedDependencyRule to each sentence
        for (Sentence sentence : document.getSentences()) {
            for (TypedDependencyRule rule : rules) {   // inject all TDR beans
                rule.analyzeAndMatch(sentence);
            }
        }

        // Phase 2: Create Candidate objects from THIS document's RuleMatches only.
        // Scoping to the current document (instead of listAll() over the whole
        // corpus) keeps each analysis pass proportional to the document's own
        // matches/candidates; previously every pass re-scanned the accumulated
        // set, so total load time grew quadratically with the document count.
        final List<Candidate> candidates = ruleMatches.createCandidatesFrom(
                ruleMatches.listAllForDocument(document), domainModel);
        log.info("Created {} candidates from rule matches for '{}'", candidates.size(), document.getTitle());

        // Phase 2b: Archetype classification (Coad et al. 1999)
        for (Candidate candidate : candidates) {
            if (candidate instanceof ClassCdd classCdd) {
                ClassType suggested = archetypeClassifier.suggestArchetype(classCdd);
                classCdd.setClassType(suggested);
                log.debug("Archetype classifier: {} → {}", classCdd.getCandidateName(), suggested);
            }
        }

        // Phase 2c: Late-binding resolution (Option C) — for ambiguous nouns
        // that appear as entities but have attribute-like dependency evidence,
        // create additional AttributeCdd candidates so the user can review both.
        // Resolution is scoped to the candidates touched by this document.
        candidateResolver.resolve(domainModel, candidates);
    }

    /**
     * Loads a single UC document into the given corpus and runs the analysis
     * pipeline, unless the content has already been analysed (duplicate guard).
     *
     * @param filename classpath resource name of the {@code UC*.md} file
     * @param corpus   the corpus that owns the loaded document
     * @return {@code true} when a new document was loaded; {@code false} when the
     * content was already present and was skipped as a duplicate
     */
    private boolean loadIntoCorpus(final String filename, final Corpus corpus) {
        final String mdContent = new FileUtil().readFileFromResources(filename);
        final String txtContent = new TextFilter().stripMarkdownRegex(mdContent);

        // Guard against re-analysing a document that was already loaded. Running the
        // load actions twice previously created duplicate Documents + DomainModels and
        // re-derived every Candidate (the whole candidate set was duplicated ×2).
        if (documents.existsByContent(txtContent)) {
            log.info("Sample '{}' already analysed (duplicate content detected); skipping.", filename);
            return false;
        }

        final Clob content = new Clob("", "text/xml", txtContent);
        final List<Author> authors = new ArrayList<>();
        authors.add(new Author());
        final Document document = build(filename, filename, content, authors);
        document.setFileName(filename);
        attachToCorpus(corpus, document);
        analyzeDocument(document);
        repositoryService.persistAndFlush(corpus);
        return true;
    }

    /**
     * Associates a document with its corpus (owning side: the document's corpus FK).
     */
    private void attachToCorpus(final Corpus corpus, final Document document) {
        document.setCorpus(corpus);
        corpus.addDocument(document);
    }

    private Document build(String title, String url, Clob content, List<Author> authors) {
        final Document document = documents.create(title, url, content, authors);
        final String rawText = document.getContent();
        final DocumentTO documentTO = nlpPort.parse(rawText);
        repositoryService.persistAndFlush(document);
        List<Sentence> sentences = documents.createSentences(document, documentTO);
        document.setSentences(sentences);
        return document;
    }

}