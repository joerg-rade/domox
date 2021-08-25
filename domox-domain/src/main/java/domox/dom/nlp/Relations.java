package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Relations")
@RequiredArgsConstructor
public class Relations {
    private final RepositoryService repositoryService;
    private final RelationRepository repository;

    @PropertyLayout(sequence = "1")
    public List<Relation> listAll() {
        return repositoryService.allInstances(Relation.class);
    }

    @PropertyLayout(sequence = "2")
    public Relation create() {
        final Relation obj = repositoryService.detachedEntity(Relation.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    public List<Relation> findByType(final RelationType type) {
        return repository.findByType(type);
    }

}