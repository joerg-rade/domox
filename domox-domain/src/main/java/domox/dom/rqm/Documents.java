package domox.dom.rqm;

import domox.Constants;
import domox.DomainModule;
import domox.diagram.DiagramBuilder;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.AssociationCdd;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModel;
import domox.dom.crc.PropertyCdd;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TypedDependency;
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

    private final RepositoryService repositoryService;
    private final Sentences sentences;
    private final DiagramBuilder diagramBuilder;
    private final MessageService messageService;

    @Inject
    public Documents(RepositoryService repositoryService,
                     Sentences sentences,
                     DiagramBuilder diagramBuilder,
                     MessageService messageService) {
        this.repositoryService = repositoryService;
        this.sentences = sentences;
        this.diagramBuilder = diagramBuilder;
        this.messageService = messageService;
    }

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
    public List<Document> findByTitle(final String title) {
        List<Document> answer = new ArrayList<>();
        for (Document d : listAll()) {
            if (d.getTitle().equals(title)) {
                answer.add(d);
            }
        }
        return answer;
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
     * Renders a document-wide <em>lexical dependency</em> diagram (PDF) whose nodes are
     * restricted to the words for which a {@link domox.dom.crc.Candidate} was created in
     * the document's shared {@link DomainModel}.
     * <p>
     * Content words (nouns, verbs, adjectives) are only included when their lemma matches
     * a created candidate.  Class candidates participate only if their
     * {@link ClassCdd#getRuleMatchCount() rule-match count} is at least {@code threshold};
     * action, property and association candidates are always eligible.  Edges are drawn
     * directly from the persisted {@link TypedDependency}s of the document's sentences —
     * a dependency participates only when <em>both</em> its governor and dependent lemma
     * are candidate words — so no `SentenceTO`/`TokenTO` reconstruction is needed.
     *
     * @param document the document whose sentences are to be diagrammed
     * @param threshold minimum rule-match count for {@link ClassCdd} candidates to be
     *                  treated as an eligible word
     * @return a PDF {@link Blob}, or {@code null} if the document has no domain model
     */
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(
            sequence = "4",
            cssClassFa = "project-diagram",
            describedAs = "Render a document-wide lexical dependency graph (PDF) restricted to candidate words")
    public Blob renderLexicalDiagram(
            @ParameterLayout(named = "Document") final Document document,
            @ParameterLayout(named = "Class rule-match threshold") final int threshold) {
        if (document == null) {
            messageService.warnUser("Please choose a document to diagram.");
            return null;
        }
        final DomainModel model = document.getDomainModel();
        if (model == null) {
            messageService.warnUser("Document '" + document.getTitle() + "' has no domain model yet — run Analysis first.");
            return null;
        }

        final Set<String> allowedLemmas = candidateLemmas(model, threshold);
        final List<TypedDependency> dependencies = sentences.findByDocument(document).stream()
                .flatMap(sentence -> sentence.getTypedDependencies() != null
                        ? sentence.getTypedDependencies().stream()
                        : java.util.stream.Stream.empty())
                .filter(td -> isCandidateDependency(td, allowedLemmas))
                .collect(Collectors.toList());

        final String dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies);
        final byte[] bytes = diagramBuilder.buildLexicalGraphDiagram(dotCode);
        final String fileName = document.getTitle() + "-lexical.pdf";
        return new Blob(fileName, Constants.pdfMimeType, bytes);
    }

    @MemberSupport
    public int default1RenderLexicalDiagram() {
        return 1;
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