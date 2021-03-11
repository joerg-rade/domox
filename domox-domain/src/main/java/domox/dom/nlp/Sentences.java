package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Sentences")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Sentences {
    private final RepositoryService repositoryService;
    private final ModelDependencyRepository repository;

    @MemberOrder(sequence = "1")
    public List<Sentence> listAll() {
        return repositoryService.allInstances(Sentence.class);
    }

    @MemberOrder(sequence = "2")
    public Sentence create() {
        final Sentence obj = repositoryService.detachedEntity(Sentence.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

}