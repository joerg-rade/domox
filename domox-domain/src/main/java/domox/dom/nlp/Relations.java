package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.NatureOfService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Relations")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Relations {

    private final RepositoryService repositoryService;
    private final RelationRepository relationRepository;

    @ActionLayout(sequence = "1")
    public List<Relation> listAll() {
        return relationRepository.findAll();
    }

    @ActionLayout(sequence = "2")
    public Relation create() {
        final Relation obj = new Relation();
        repositoryService.persist(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    public List<Relation> findByType(final RelationType type) {
        return relationRepository.findByType(type);
    }

}