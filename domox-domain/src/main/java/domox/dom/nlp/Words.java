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
        objectType = "domox.PartOfSpeeches")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Words {
    private final RepositoryService repositoryService;
    private final WordRepository repository;

    @MemberOrder(sequence = "1")
    public List<Word> listAll() {
        return repositoryService.allInstances(Word.class);
    }

    @MemberOrder(sequence = "2")
    public Word create() {
        final Word obj = repositoryService.detachedEntity(Word.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
    public List<Word> findByType(final PosType type) {
        return repository.findByType(type);
    }

}