package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PriorityPrecedence;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.factory.FactoryService;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.services.JpaSupportService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.Relations")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
public class Relations {

    @Inject private RepositoryService repositoryService;
    @Inject private JpaSupportService jpaSupportService;
    @Inject private RelationRepository repository;
    @Inject private FactoryService factoryService;

    @PropertyLayout(sequence = "1")
    public List<Relation> listAll() {
        return repositoryService.allInstances(Relation.class);
    }

    @PropertyLayout(sequence = "2")
    public Relation create() {
        final Relation obj = factoryService.detachedEntity(Relation.class);
        repositoryService.persist(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    public List<Relation> findByType(final RelationType type) {
        return repository.findByType(type);
    }

}