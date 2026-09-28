package domox.dom.rqm;

import domox.DomainModule;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.nlp.DocumentTO;
import domox.nlp.SentenceTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Named(DomainModule.NAMESPACE + ".Documents")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class Documents {

    private final RepositoryService repositoryService;
    private final Sentences sentences;

    @Inject
    public Documents(RepositoryService repositoryService, Sentences sentences) {
        this.repositoryService = repositoryService;
        this.sentences = sentences;
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

    @Programmatic
    public void deleteAll() {
        var all = listAll();
        for (Document d : all) {
            repositoryService.remove(d);
        }
    }
}