package domox.dom.rqm;

import domox.DomainModule;
import domox.FileUtil;
import domox.HtmlReader;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.Sentences;
import domox.nlp.DocumentTO;
import domox.nlp.SentenceTO;
import domox.svc.DocumentAdapter;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.SemanticsOf;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Named(DomainModule.NAMESPACE + ".Documents")
@DomainService
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Documents {

    private final RepositoryService repositoryService;
    private final Sentences sentences;

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
        obj.setContent(content.getChars().toString());
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

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "play")
    public Document loadUrlSample() {
        //https://www.reqview.com/papers/ReqView-Example_Software_Requirements_Specification_SRS_Document.pdf
        //final String url = "https://web.cse.ohio-state.edu/~bair.41/616/Project/Example_Document/Req_Doc_Example.html";
        final String url = "https://en.wikipedia.org/wiki/Logistics";
        final String title = "Requirements Document Example";

        final HtmlReader reader = new HtmlReader();
        final String txtContent = reader.extractContentFromUrl(url);
        final Clob content = new Clob("", "text/xml", txtContent);
        return build(title, url, content, null);
    }

    private Document build(String title, String url, Clob content, List<Author> authors) {
        final Document document = create(title, url, content, authors);
        final String rawText = document.getContent();
        final DocumentTO documentTO = new DocumentAdapter().parseTextAndAmend(rawText);
        repositoryService.persistAndFlush(document);
        List<Sentence> sentences = createSentences(document, documentTO);
        document.setSentences(sentences);
        return document;
    }

    public List<Sentence> createSentences(Document document, DocumentTO to) {
        final List<SentenceTO> toList = to.getSentences();
        final List<Sentence> sentenceList = new ArrayList<>();
        for (SentenceTO st : toList) {
            final Sentence sentence = sentences.build(st);
            sentence.setDocument(document);
            sentenceList.add(sentence);
            sentences.initDiagram(st, sentence);
        }
        return sentenceList;
    }

    @Action()
    @ActionLayout(sequence = "5", cssClassFa = "play")
    public Document loadFileSample() {
        final String title = "Pet Shop Use Cases";
        final String filename = "PetShop_useCases.txt";
        final String txtContent = new FileUtil().readFileFromResources(filename);
        final Clob content = new Clob("", "text/xml", txtContent);
        final Author author = new Author();
        final List<Author> authors = new ArrayList<>();
        authors.add(author);
        return build(title, filename, content, authors);
    }

    @Action()
    @ActionLayout(sequence = "6", cssClassFa = "trash")
    public void delete(Document document) {
        repositoryService.remove(document);
    }
}