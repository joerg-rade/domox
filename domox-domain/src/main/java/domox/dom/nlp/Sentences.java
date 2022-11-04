package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Sentences")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Sentences {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
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