package domox.dom.rqm;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.value.Clob;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Documents")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Documents {
    private final RepositoryService repositoryService;

    @MemberOrder(sequence = "1")
    public List<Document> listAll() {
        return repositoryService.allInstances(Document.class);
    }

    @MemberOrder(sequence = "2")
    public Document create(String title, String url, Clob content, Set<Author> authors) {
        final Document obj = repositoryService.detachedEntity(Document.class);
        obj.setTitle(title);
        obj.setUrl(url);
        obj.setContent(content);
        obj.setAuthors(authors);
        repositoryService.persist(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
    public List<Document> findByTitle(final String title) {
        List<Document> answer = new ArrayList<>();
        for (Document d : listAll()) {
            if (d.getTitle().equals(title)) {
                answer.add(d);
            }
        }
        return answer;
    }

}