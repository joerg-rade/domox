package domox.dom;

import domox.DomainModule;
import domox.FileUtil;
import domox.TextFilter;
import domox.dom.crc.*;
import domox.dom.nlp.Sentence;
import domox.dom.rqm.Author;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.Corpora;
import domox.dom.rqm.Document;
import domox.dom.rqm.Documents;
import domox.dom.rules.CandidateResolver;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import domox.dom.rules.TypedDependencyRule;
import domox.nlp.DocumentTO;
import domox.svc.DocumentAdapter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Analysis")
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class Analysis {
    private static final Logger log = LoggerFactory.getLogger(Analysis.class);

    private final RepositoryService repositoryService;
    private final Documents documents;
    private final Corpora corpora;
    private final RuleMatches ruleMatches;
    private final List<TypedDependencyRule> rules;
    private final DomainModels domainModels;
    private final ClassArchetypeClassifier archetypeClassifier;
    private final CandidateResolver candidateResolver;
    private final MessageService messageService;

    @Inject
    public Analysis(RepositoryService repositoryService,
                    Documents documents,
                    Corpora corpora,
                    RuleMatches ruleMatches,
                    List<TypedDependencyRule> rules,
                    DomainModels domainModels,
                    ClassArchetypeClassifier archetypeClassifier,
                    CandidateResolver candidateResolver,
                    MessageService messageService) {
        this.repositoryService = repositoryService;
        this.documents = documents;
        this.corpora = corpora;
        this.ruleMatches = ruleMatches;
        this.rules = rules;
        this.domainModels = domainModels;
        this.archetypeClassifier = archetypeClassifier;
        this.candidateResolver = candidateResolver;
        this.messageService = messageService;
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

        // Phase 2: Create Candidate objects from all RuleMatches
        final List<Candidate> candidates = ruleMatches.createCandidatesFrom(ruleMatches.listAll(), domainModel);
        log.info("Created {} candidates from rule matches", candidates.size());

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
        candidateResolver.resolve(domainModel);
    }

    /** Title of the single Corpus that groups all UC* use-case documents. */
    private static final String CORPUS_TITLE = "Pet Shop Use Cases";

    /** Classpath pattern selecting every UC* markdown document in the resources. */
    private static final String UC_RESOURCE_PATTERN = "classpath*:UC*.md";

    @Action()
    @ActionLayout(sequence = "5", cssClassFa = "play")
    public List<RuleMatch> loadFileSample() {
        final Corpus corpus = corpus();
        log.info("Loading sample into corpus '{}' (id {}).", CORPUS_TITLE, corpus.getId());

        int loaded = 0;
        for (final String filename : loadUcFilenames()) {
            if (loadIntoCorpus(filename, corpus)) {
                loaded++;
            }
        }

        log.info("Loaded {} new UC* documents into corpus '{}'.", loaded, CORPUS_TITLE);
        if (loaded == 0) {
            messageService.informUser("All sample documents have already been analysed. Skipping duplicates.");
        }
        return ruleMatches.listAll();
    }

    /**
     * Loads a single selected {@code UC*.md} classpath document into the corpus and
     * analyses it, running the full rule pipeline (match → candidate creation →
     * archetype classification → late-binding resolution).
     *
     * <p>The selectable filenames are exposed to the UI via
     * {@link #choices0LoadUcDocument()}, defaulting to the first file.</p>
     *
     * @param filename a {@code UC*.md} filename present in the classpath resources
     */
    @Action()
    @ActionLayout(sequence = "5.2", cssClassFa = "file-import")
    public List<RuleMatch> loadUcDocument(
            @ParameterLayout(named = "UC Document") final String filename) {
        final Corpus corpus = corpus();
        if (loadIntoCorpus(filename, corpus)) {
            log.info("Loaded UC document '{}' into corpus '{}'.", filename, corpus.getTitle());
        } else {
            messageService.informUser("Document '" + filename + "' has already been analysed; skipping duplicate.");
        }
        return ruleMatches.listAll();
    }

    /** Selectable UC* filenames offered as choices for {@link #loadUcDocument(String)}. */
    @MemberSupport
    public List<String> choices0LoadUcDocument() {
        return loadUcFilenames();
    }

    /** Pre-selects the first UC* document for {@link #loadUcDocument(String)}. */
    @MemberSupport
    public String default0LoadUcDocument() {
        final List<String> filenames = loadUcFilenames();
        return filenames.isEmpty() ? null : filenames.get(0);
    }

    /** The single Corpus that groups all UC* use-case documents. */
    private Corpus corpus() {
        return corpora.findByTitle(CORPUS_TITLE).stream()
                .findFirst()
                .orElseGet(() -> corpora.create(CORPUS_TITLE));
    }

    /**
     * Loads a single UC document into the given corpus and runs the analysis
     * pipeline, unless the content has already been analysed (duplicate guard).
     *
     * @param filename classpath resource name of the {@code UC*.md} file
     * @param corpus   the corpus that owns the loaded document
     * @return {@code true} when a new document was loaded; {@code false} when the
     *         content was already present and was skipped as a duplicate
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
        attachToCorpus(corpus, document);
        analyzeDocument(document);
        repositoryService.persistAndFlush(corpus);
        return true;
    }

    /**
     * Discovers the individual UC* markdown files on the classpath and returns their
     * filenames (used both as the resource path passed to {@link FileUtil} and as the
     * {@link Document} title), sorted for a deterministic processing order.
     */
    private List<String> loadUcFilenames() {
        try {
            final Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(UC_RESOURCE_PATTERN);
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to enumerate UC* documents from '" + UC_RESOURCE_PATTERN + "'", e);
        }
    }

    /** Associates a document with its corpus (owning side: the document's corpus FK). */
    private void attachToCorpus(final Corpus corpus, final Document document) {
        document.setCorpus(corpus);
        corpus.addDocument(document);
    }

    @Action()
    @ActionLayout(sequence = "6", cssClassFa = "trash")
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

    /** Selectable corpora offered as choices for {@link #deleteCorpus(Corpus)}. */
    @MemberSupport
    public List<Corpus> choices0DeleteCorpus() {
        return repositoryService.allInstances(Corpus.class);
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
