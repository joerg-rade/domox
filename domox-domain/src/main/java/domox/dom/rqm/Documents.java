package domox.dom.rqm;

import domox.SimpleModule;
import domox.dom.nlp.Sentence;
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

    public void addSentenceTo(String raw, Document document) {
        final Sentence obj = repositoryService.detachedEntity(Sentence.class);
        obj.setRaw(raw);
        repositoryService.persistAndFlush(obj);
//        obj.setDocument(document);
        document.getSentences().add(obj);
        repositoryService.persistAndFlush(document);
    }

}