package domox.dom.rqm;

import domox.DomainModule;
import domox.FileUtil;
import domox.TextFilter;
import domox.dom.UcResources;
import domox.dom.crc.Candidate;
import domox.dom.crc.ClassArchetypeClassifier;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.ClassType;
import domox.dom.crc.DomainModel;
import domox.dom.crc.DomainModels;
import domox.dom.nlp.Sentence;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.TypedDependencyRule;
import domox.nlp.DocumentTO;
import domox.svc.DocumentAdapter;
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
import java.util.ArrayList;
import java.util.List;

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

    @Inject
    public Corpora(RepositoryService repositoryService,
                   FactoryService factoryService,
                   RuleMatches ruleMatches,
                   Documents documents,
                   List<TypedDependencyRule> rules,
                   DomainModels domainModels,
                   ClassArchetypeClassifier archetypeClassifier,
                   CandidateResolver candidateResolver) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.ruleMatches = ruleMatches;
        this.documents = documents;
        this.rules = rules;
        this.domainModels = domainModels;
        this.archetypeClassifier = archetypeClassifier;
        this.candidateResolver = candidateResolver;
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
        // rows (e.g. ActionCdd with classCdd = null) live only in the model's own
        // collections; relying solely on the model's @OneToMany(cascade = ALL) to
        // cascade-delete those lazy collections is unreliable — EclipseLink can
        // issue the DELETE on the DomainModel before the still-referencing children
        // are gone, tripping the DB FK constraints
        // (e.g. FK_ActionCdd_DOMAINMODEL_ID).  Registering each candidate for
        // removal explicitly — in child-before-parent order — guarantees the FK
        // constraints cannot fire.
        final DomainModel domainModel = corpus.getDomainModel();
        if (domainModel != null) {
            removeCandidates(domainModel.getAssociationList());
            removeCandidates(domainModel.getActionList());
            removeCandidates(domainModel.getPropertyList());
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
        // create additional PropertyCdd candidates so the user can review both.
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
        final DocumentTO documentTO = new DocumentAdapter().parseTextAndAmend(rawText);
        repositoryService.persistAndFlush(document);
        List<Sentence> sentences = documents.createSentences(document, documentTO);
        document.setSentences(sentences);
        return document;
    }

}