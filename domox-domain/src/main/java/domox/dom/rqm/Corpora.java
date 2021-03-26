package domox.dom.rqm;

import domox.SimpleModule;
import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Corpora")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Corpora {
    private final RepositoryService repositoryService;

    public static class ActionDomainEvent extends SimpleModule.ActionDomainEvent<Corpora> {
    }

    public static class CreateActionDomainEvent extends Corpora.ActionDomainEvent {
    }

    @MemberOrder(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @MemberOrder(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT, domainEvent = Corpora.CreateActionDomainEvent.class)
    @ActionLayout
    public Corpus create(String title) {
        final Corpus obj = repositoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
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