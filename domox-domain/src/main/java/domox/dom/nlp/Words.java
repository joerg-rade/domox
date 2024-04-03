package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.PartOfSpeeches")
@jakarta.annotation.Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Words {

    private final RepositoryService repositoryService;
    private final WordRepository repository;
    private final FactoryService factoryService;

    @ActionLayout(sequence = "1")
    public List<Word> listAll() {
        return repositoryService.allInstances(Word.class);
    }

    @ActionLayout(sequence = "2")
    public Word create() {
        final Word obj = factoryService.detachedEntity(Word.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    public List<Word> findByType(final PosType type) {
        return repository.findByType(type);
    }

}