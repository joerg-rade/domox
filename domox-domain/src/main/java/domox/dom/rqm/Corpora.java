package domox.dom.rqm;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Corpora")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Corpora {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @ActionLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Corpus create(String title) {
        final Corpus obj = factoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> findByTitle(final String title) {
        List<Corpus> answer = new ArrayList<>();
        for (Corpus o : listAll()) {
            if (o.getTitle().equals(title)) {
                answer.add(o);
            }
        }
        return answer;
    }

}