package domox.dom.rqm;

import domox.svc.HtmlDocumentReader;
import domox.SimpleModule;
import domox.dom.nlp.Sentence;
import domox.svc.NlpAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.value.Clob;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Documents")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Documents {
    private final RepositoryService repositoryService;

    public static class ActionDomainEvent extends SimpleModule.ActionDomainEvent<Documents> {
    }

    public static class CreateActionDomainEvent extends Documents.ActionDomainEvent {
    }

    @MemberOrder(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Document> listAll() {
        return repositoryService.allInstances(Document.class);
    }

    @MemberOrder(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT, domainEvent = Documents.CreateActionDomainEvent.class)
    @ActionLayout
    public Document create(String title, String url, Clob content, Set<Author> authors) {
        final Document obj = repositoryService.detachedEntity(Document.class);
        obj.setTitle(title);
        obj.setUrl(url);
        obj.setContent(content);
        obj.setAuthors(authors);
        obj.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        obj.setDocVersion("1.0.0");
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Document> findByTitle(final String title) {
        List<Document> answer = new ArrayList<>();
        for (Document d : listAll()) {
            if (d.getTitle().equals(title)) {
                answer.add(d);
            }
        }
        return answer;
    }

    @MemberOrder(sequence = "4")
    @Action()
    @ActionLayout(cssClassFa = "play")
    public Document loadSample() {
        //https://www.reqview.com/papers/ReqView-Example_Software_Requirements_Specification_SRS_Document.pdf
        final String url = "https://web.cse.ohio-state.edu/~bair.41/616/Project/Example_Document/Req_Doc_Example.html";
        final String title = "Requirements Document Example";

        final HtmlDocumentReader reader = new HtmlDocumentReader();
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
        final Sentence obj = repositoryService.detachedEntity(Sentence.class);
        obj.setRaw(raw);
        repositoryService.persistAndFlush(obj);
        document.getSentences().add(obj);
        repositoryService.persistAndFlush(document);
    }

}