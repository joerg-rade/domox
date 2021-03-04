package domox.dom.rqm;

import domox.SimpleModule;
import domox.types.Name;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jdo.applib.services.IsisJdoSupport_v3_2;

import javax.inject.Inject;
import javax.jdo.JDOQLTypedQuery;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.Author"
)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Authors {

    private final RepositoryService repositoryService;
    private final IsisJdoSupport_v3_2 isisJdoSupport;

    public static class ActionDomainEvent extends SimpleModule.ActionDomainEvent<Authors> {
    }

    public static class CreateActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT, domainEvent = CreateActionDomainEvent.class)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public Author create(
            @Name final String name) {
        return repositoryService.persist(Author.withName(name));
    }

    public static class FindByNameActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.SAFE, domainEvent = FindByNameActionDomainEvent.class)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT, promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public List<Author> findByName(
            @Name final String name
    ) {
        JDOQLTypedQuery<Author> q = isisJdoSupport.newTypesafeQuery(Author.class);
        final QAuthor cand = QAuthor.candidate();
        q = q.filter(
                cand.lastName.indexOf(q.stringParameter("name")).ne(-1)
        );
        return q.setParameter("name", name)
                .executeList();
    }

    @Programmatic
    public Author findByNameExact(final String name) {
        JDOQLTypedQuery<Author> q = isisJdoSupport.newTypesafeQuery(Author.class);
        final QAuthor cand = QAuthor.candidate();
        q = q.filter(
                cand.lastName.eq(q.stringParameter("name"))
        );
        return q.setParameter("name", name)
                .executeUnique();
    }

    public static class ListAllActionDomainEvent extends ActionDomainEvent {
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT)
    public List<Author> listAll() {
        return repositoryService.allInstances(Author.class);
    }

    @Programmatic
    public void ping() {
        JDOQLTypedQuery<Author> q = isisJdoSupport.newTypesafeQuery(Author.class);
        final QAuthor candidate = QAuthor.candidate();
        q.range(0, 2);
        q.orderBy(candidate.lastName.asc());
        q.executeList();
    }

}
