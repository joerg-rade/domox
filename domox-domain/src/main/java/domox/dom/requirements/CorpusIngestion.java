package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.UcResources;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.ClassArchetypeClassifier;
import domox.dom.crc.service.DomainModels;
import domox.dom.nlp.Sentence;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.TypedDependencyRule;
import domox.port.DocumentTO;
import domox.port.NlpPort;
import domox.util.FileUtil;
import domox.util.TextFilter;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The <b>requirements-ingestion</b> service: loads a {@code UC*.md} use-case document and runs the
 * full analysis pipeline over it — NLP parse → TDR rule matching → candidate creation → archetype
 * classification → late-binding resolution.
 *
 * <p><b>Why it is its own service.</b>  This is the pipeline axis: it changes when a phase changes
 * (a new rule, a new classifier, a different de-duplication guard) and for no other reason.  It used
 * to live on {@code Corpora} together with corpus lifecycle and the ontology diagram; splitting it
 * leaves each service with one reason to change.</p>
 *
 * <p>It depends on {@link Corpora} for the one thing that <em>is</em> corpus lifecycle: finding or
 * creating the single "Pet Shop Use Cases" corpus that owns every loaded document.</p>
 */
@Named(DomainModule.NAMESPACE + ".CorpusIngestion")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CorpusIngestion {

    private static final Logger log = LoggerFactory.getLogger(CorpusIngestion.class);

    /**
     * Title of the single Corpus that groups all UC* use-case documents.
     */
    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    private final RepositoryService repositoryService;
    private final Corpora corpora;
    private final Documents documents;
    private final List<TypedDependencyRule> rules;
    private final DomainModels domainModels;
    private final ClassArchetypeClassifier archetypeClassifier;
    private final CandidateResolver candidateResolver;
    private final NlpPort nlpPort;
    private final RuleMatches ruleMatches;

    @Inject
    public CorpusIngestion(
            final RepositoryService repositoryService,
            final Corpora corpora,
            final Documents documents,
            final List<TypedDependencyRule> rules,
            final DomainModels domainModels,
            final ClassArchetypeClassifier archetypeClassifier,
            final CandidateResolver candidateResolver,
            final NlpPort nlpPort,
            final RuleMatches ruleMatches) {
        this.repositoryService = repositoryService;
        this.corpora = corpora;
        this.documents = documents;
        this.rules = rules;
        this.domainModels = domainModels;
        this.archetypeClassifier = archetypeClassifier;
        this.candidateResolver = candidateResolver;
        this.nlpPort = nlpPort;
        this.ruleMatches = ruleMatches;
    }

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
        return corpora.findByTitle(CORPUS_TITLE).stream()
                .findFirst()
                .orElseGet(() -> corpora.create(CORPUS_TITLE));
    }

    // ======================================================================
    // The analysis pipeline
    // ======================================================================

    private void analyzeDocument(final Document document) {
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
