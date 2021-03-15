package domox.dom.rqm;

import domox.SimpleModule;
import domox.types.Name;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.services.JpaSupportService;

import javax.inject.Inject;
import javax.persistence.TypedQuery;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Authors"
)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Authors {

    private final RepositoryService repositoryService;
    private final JpaSupportService jpaSupportService;
    private final AuthorRepository authorRepository;

    public static class ActionDomainEvent extends SimpleModule.ActionDomainEvent<Authors> {
    }

    public static class CreateActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT, domainEvent = CreateActionDomainEvent.class)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public Author create(
            @Name final String name) {
        return repositoryService.persistAndFlush(Author.withLastName(name));
    }

    public static class FindByNameActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.SAFE, domainEvent = FindByNameActionDomainEvent.class)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT, promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public List<Author> findByName(
            @Name final String name
    ) {
        return authorRepository.findByLastNameContaining(name);
    }

    @Programmatic
    public Author findByNameExact(final String name) {
        return authorRepository.findByLastName(name);
    }

    public static class ListAllActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT)
    public List<Author> listAll() {
        return authorRepository.findAll();
    }


    @Programmatic
    public void ping() {
        jpaSupportService.getEntityManager(Author.class).ifSuccess(x -> {
            final TypedQuery<Author> q = x.createQuery("SELECT p FROM Author p ORDER BY p.lastName",
                    Author.class).setMaxResults(1);
            q.getResultList();
        });
    }

}
