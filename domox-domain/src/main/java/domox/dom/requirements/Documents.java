package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.UcResources;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.rules.RuleMatch;
import domox.dom.rules.RuleMatches;
import domox.port.DocumentTO;
import domox.port.SentenceTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Named(DomainModule.NAMESPACE + ".Documents")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class Documents {

    private static final Logger log = LoggerFactory.getLogger(Documents.class);

    private final RepositoryService repositoryService;
    private final Sentences sentences;
    private final MessageService messageService;
    private final RuleMatches ruleMatches;

    @Inject
    public Documents(RepositoryService repositoryService,
                     Sentences sentences,
                     MessageService messageService,
                     RuleMatches ruleMatches) {
        this.repositoryService = repositoryService;
        this.sentences = sentences;
        this.messageService = messageService;
        this.ruleMatches = ruleMatches;
    }

    /**
     * Mutually-injected collaborator: {@link CorpusIngestion} constructor-injects {@code Documents}
     * (its analyse pipeline creates {@link Document}s and {@link Sentence}s), while the
     * {@code Documents} menu delegates its single- and batch-load actions
     * ({@link #loadUcDocument(String)}.
     * Constructor injection would therefore be an unresolvable Spring cycle, so the
     * dependency is field-injected and flagged {@code @Lazy}: Spring injects a proxy that
     * resolves to the real {@code CorpusIngestion} bean only when an action invokes it at runtime.
     */
    @Lazy
    @Inject
    private CorpusIngestion corpusIngestion;

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
        return filenames.isEmpty() ? null : filenames.getFirst();
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
        if (corpusIngestion.loadUcDocument(filename)) {
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
        return filenames.isEmpty() ? null : filenames.getFirst();
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

    @Programmatic
    public void deleteAll() {
        var all = listAll();
        for (Document d : all) {
            repositoryService.remove(d);
        }
    }
}