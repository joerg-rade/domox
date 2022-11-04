package domox.dom.nlp;

import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.Sentences")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
public class Sentences {

    @Inject
    private RepositoryService repositoryService;
    //    @Inject private JpaSupportService jpaSupportService;
    @Inject
    private FactoryService factoryService;

    @PropertyLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Sentence> listAll() {
        return repositoryService.allInstances(Sentence.class);
    }

    @Programmatic
    public Sentence create() {
        final Sentence obj = factoryService.detachedEntity(Sentence.class);
        repositoryService.persist(obj);
        return obj;
    }

}