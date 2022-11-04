package domox.dom.rqm;

import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.Corpora")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
public class Corpora {

    @Inject
    private RepositoryService repositoryService;
    @Inject
    private FactoryService factoryService;

    @PropertyLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @PropertyLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    @ActionLayout
    public Corpus create(String title) {
        final Corpus obj = factoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
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