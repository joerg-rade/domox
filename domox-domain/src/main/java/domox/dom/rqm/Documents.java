package domox.dom.rqm;

import domox.HtmlReader;
import domox.dom.nlp.Sentence;
import domox.svc.NlpAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Documents")
@jakarta.annotation.Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Documents {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;


    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Document> listAll() {
        return repositoryService.allInstances(Document.class);
    }

    @ActionLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Document create(String title, String url, Clob content, Set<Author> authors) {
        final Document obj = factoryService.detachedEntity(Document.class);
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
    public Document loadSample() {
        //https://www.reqview.com/papers/ReqView-Example_Software_Requirements_Specification_SRS_Document.pdf
        final String url = "https://web.cse.ohio-state.edu/~bair.41/616/Project/Example_Document/Req_Doc_Example.html";
        final String title = "Requirements Document Example";

        final HtmlReader reader = new HtmlReader();
        final String txtContent = reader.extractContentFromUrl(url);
        final Clob content = new Clob("", "text/xml", txtContent);
        final Document document = create(title, url, content, null);
        NlpAdapter.parseTextAndAmend(document);
        repositoryService.persistAndFlush(document);
//        final List<String> rawList = reader.split(document);
//        for (String r :rawList) {
//            addSentenceTo(r, document);
//        }
        return document;
    }

    public void addSentenceTo(String raw, Document document) {
        final Sentence obj = factoryService.detachedEntity(Sentence.class);
        obj.setText(raw);
        repositoryService.persistAndFlush(obj);
        document.getSentences().add(obj);
        repositoryService.persistAndFlush(document);
    }

}