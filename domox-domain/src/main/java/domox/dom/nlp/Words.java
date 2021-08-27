package domox.dom.nlp;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PriorityPrecedence;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.factory.FactoryService;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.PartOfSpeeches")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject} )
public class Words {

    private final RepositoryService repositoryService;
    private final WordRepository repository;
    private final FactoryService factoryService;

    @PropertyLayout(sequence = "1")
    public List<Word> listAll() {
        return repositoryService.allInstances(Word.class);
    }

    @PropertyLayout(sequence = "2")
    public Word create() {
        final Word obj = factoryService.detachedEntity(Word.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    public List<Word> findByType(final PosType type) {
        return repository.findByType(type);
    }

}