package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.PartOfSpeeches")
@RequiredArgsConstructor
public class Words {
    private final RepositoryService repositoryService;
    private final WordRepository repository;

    @PropertyLayout(sequence = "1")
    public List<Word> listAll() {
        return repositoryService.allInstances(Word.class);
    }

    @PropertyLayout(sequence = "2")
    public Word create() {
        final Word obj = repositoryService.detachedEntity(Word.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    public List<Word> findByType(final PosType type) {
        return repository.findByType(type);
    }

}