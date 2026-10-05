package domox.dom.rqm;

import domox.Constants;
import domox.DomainModule;
import domox.diagram.DiagramBuilder;
import domox.dom.UcResources;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.AssociationCdd;
import domox.dom.crc.Candidate;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModel;
import domox.dom.crc.PropertyCdd;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import domox.nlp.DocumentTO;
import domox.nlp.SentenceTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Named(DomainModule.NAMESPACE + ".Documents")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class Documents {

    private static final Logger log = LoggerFactory.getLogger(Documents.class);

    /** Default rule-match threshold used when lazily building a document's lexical diagram. */
    private static final int DEFAULT_LEXICAL_THRESHOLD = 1;

    private final RepositoryService repositoryService;
    private final Sentences sentences;
    private final DiagramBuilder diagramBuilder;
    private final MessageService messageService;
    private final RuleMatches ruleMatches;

    @Inject
    public Documents(RepositoryService repositoryService,
                     Sentences sentences,
                     DiagramBuilder diagramBuilder,
                     MessageService messageService,
                     RuleMatches ruleMatches) {
        this.repositoryService = repositoryService;
        this.sentences = sentences;
        this.diagramBuilder = diagramBuilder;
        this.messageService = messageService;
        this.ruleMatches = ruleMatches;
    }

    /**
     * Mutually-injected collaborator: {@link Corpora} constructor-injects {@code Documents}
     * (its analyse pipeline creates {@link Document}s and {@link Sentence}s), while the
     * {@code Documents} menu delegates its single- and batch-load actions
     * ({@link #loadUcDocument(String)}.
     * Constructor injection would therefore be an unresolvable Spring cycle, so the
     * dependency is field-injected and flagged {@code @Lazy}: Spring injects a proxy that
     * resolves to the real {@code Corpora} bean only when an action invokes it at runtime.
     */
    @Lazy
    @Inject
    private Corpora corpora;

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Document> listAll() {
        return repositoryService.allInstances(Document.class);
    }

    @ActionLayout(sequence = "2")
    @Action//(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Document create(String title, String url, Clob content, List<Author> authors) {
        final Document obj = new Document();
        obj.setTitle(title);
        obj.setUrl(url);
        obj.setContent(content.chars().toString());
        obj.setAuthors(authors);
        obj.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        obj.setDocVersion("1.0.0");
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Document> findByFileName(
            @ParameterLayout(named = "File Name") final String fileName) {
        List<Document> answer = new ArrayList<>();
        for (Document d : listAll()) {
            if (fileName != null && fileName.equals(d.getFileName())) {
                answer.add(d);
            }
        }
        return answer;
    }

    /**
     * Dropdown of every {@code UC*.md} use-case file available on the classpath, so the user
     * can pick the source file and retrieve the {@link Document}(s) loaded from it.
     */
    @MemberSupport
    public List<String> choices0FindByFileName() {
        return UcResources.listUcFilenames();
    }

    /** Pre-selects the first {@code UC*.md} file. */
    @MemberSupport
    public String default0FindByFileName() {
        final List<String> filenames = UcResources.listUcFilenames();
        return filenames.isEmpty() ? null : filenames.get(0);
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
    @ActionLayout(sequence = "5", cssClassFa = "file-import")
    public List<RuleMatch> loadUcDocument(
            @ParameterLayout(named = "UC Document") final String filename) {
        if (corpora.loadUcDocument(filename)) {
            log.info("Loaded UC document '{}'.", filename);
        } else {
            messageService.informUser("Document '" + filename + "' has already been analysed; skipping duplicate.");
        }
        return ruleMatches.listAll();
    }

    @MemberSupport
    public List<String> choices0LoadUcDocument() {
        return UcResources.listUcFilenames();
    }

    /** Pre-selects the first {@code UC*.md} document for {@link #loadUcDocument(String)}. */
    @MemberSupport
    public String default0LoadUcDocument() {
        final List<String> filenames = UcResources.listUcFilenames();
        return filenames.isEmpty() ? null : filenames.get(0);
    }

    /**
     * Returns whether a {@link Document} whose content equals {@code content} already exists.
     * <p>
     * Used to guard against re-analysing the same requirements text, which previously created a
     * second {@code Document} + {@code DomainModel} and re-derived every {@code Candidate},
     * duplicating the whole candidate set (e.g. running {@code loadFileSample()} twice).
     */
    @Programmatic
    public boolean existsByContent(final String content) {
        if (content == null) {
            return false;
        }
        return listAll().stream().anyMatch(d -> content.equals(d.getContent()));
    }

    @Programmatic
    public List<Sentence> createSentences(Document document, DocumentTO to) {
        final List<SentenceTO> toList = to.getSentences();
        final List<Sentence> sentenceList = new ArrayList<>();
        for (SentenceTO st : toList) {
            final Sentence sentence = sentences.build(st);
            if (null != sentence) {
                sentence.setDocument(document);
                sentenceList.add(sentence);
            }
        }
        return sentenceList;
    }

    /**
     * Renders the document-wide <em>lexical dependency</em> diagram (Kroki → PDF) for a
     * {@link Document} directly from its current persisted state.
     * <p>
     * The diagram is regenerated on <em>every</em> call — it is never cached — because its
     * content reflects the current candidate set, which changes as candidates are reviewed,
     * approved, or rejected.
     *
     * @param document the document whose sentences are to be diagrammed
     * @return a freshly rendered PDF {@link Blob}, or {@code null} if the document is null, has no
     *         domain model, or the render fails (e.g. Kroki unavailable)
     */
    @Programmatic
    public Blob renderDiagram(final Document document) {
        if (document == null || document.getDomainModel() == null) {
            return null;
        }
        final long id = document.getId();
        final String title = document.getTitle();
        log.debug("Rendering lexical diagram for use-case document #{} '{}'", id, title);
        String dotCode = null;
        try {
            final Set<String> allowedLemmas = candidateLemmas(document.getDomainModel(),
                    DEFAULT_LEXICAL_THRESHOLD);
            final List<TypedDependency> dependencies = sentences.findByDocument(document).stream()
                    .flatMap(sentence -> sentence.getTypedDependencies() != null
                            ? sentence.getTypedDependencies().stream()
                            : java.util.stream.Stream.empty())
                    .filter(td -> isCandidateDependency(td, allowedLemmas))
                    .collect(Collectors.toList());
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies,
                    candidates(document.getDomainModel()));
            final byte[] bytes = diagramBuilder.buildLexicalGraphDiagram(dotCode);
            final String fileName = document.getTitle() + "-lexical.pdf";
            return new Blob(fileName, Constants.pdfMimeType, bytes);
        } catch (Exception e) {
            // Catch Exception (not just RuntimeException): Fuel's HTTP errors (FuelError) are a checked
            // Exception, so one unrenderable document must not tear down the whole list page.
            log.warn("Failed to render lexical diagram for use-case document #{} '{}': {}",
                    id, title, e.getMessage());
            if (dotCode != null) {
                log.debug("Lexical diagram DOT for use-case document #{} '{}':\n{}", id, title, dotCode);
            }
            log.debug("Failure rendering lexical diagram for use-case document #{} '{}'", id, title, e);
            return null;
        }
    }

    /**
     * The lower-cased candidate names of the analysis run.  Class candidates are only
     * eligible when their rule-match count meets {@code threshold}; all candidate types
     * that were actually created contribute their name.
     */
    private Set<String> candidateLemmas(final DomainModel model, final int threshold) {
        final Set<String> lemmas = new HashSet<>();
        if (model.classList != null) {
            model.classList.forEach(cc -> {
                if (cc.getRuleMatchCount() >= threshold) {
                    lemmas.add(lower(cc.getCandidateName()));
                }
            });
        }
        if (model.actionList != null) {
            model.actionList.stream().map(ActionCdd::getCandidateName).forEach(a -> lemmas.add(lower(a)));
        }
        if (model.propertyList != null) {
            model.propertyList.stream().map(PropertyCdd::getCandidateName).forEach(p -> lemmas.add(lower(p)));
        }
        if (model.associationList != null) {
            model.associationList.stream().map(AssociationCdd::getCandidateName).forEach(a -> lemmas.add(lower(a)));
        }
        return lemmas;
    }

    /**
     * The candidate snapshot of the analysis run — every candidate of every type owned by the
     * model.  Passed to {@link LexicalGraphGenerator} so node borders can reflect approval status
     * and synonym membership.
     */
    private List<Candidate> candidates(final DomainModel model) {
        final List<Candidate> candidates = new ArrayList<>();
        if (model.classList != null) {
            candidates.addAll(model.classList);
        }
        if (model.actionList != null) {
            candidates.addAll(model.actionList);
        }
        if (model.propertyList != null) {
            candidates.addAll(model.propertyList);
        }
        if (model.associationList != null) {
            candidates.addAll(model.associationList);
        }
        return candidates;
    }

    /**
     * Keeps only those typed dependencies whose governor <em>and</em> dependent lemma both
     * correspond to a created candidate word — mirroring the previous token-restriction
     * step (an edge is only drawn between two retained words).
     */
    private static boolean isCandidateDependency(final TypedDependency td, final Set<String> allowedLemmas) {
        return allowedLemmas.contains(lower(td.getGovernorLemma()))
                && allowedLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @Programmatic
    public void deleteAll() {
        var all = listAll();
        for (Document d : all) {
            repositoryService.remove(d);
        }
    }
}