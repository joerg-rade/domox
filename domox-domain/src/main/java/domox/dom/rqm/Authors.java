package domox.dom.rqm;

import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.persistence.jpa.applib.services.JpaSupportService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Authors")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Authors {

    private final RepositoryService repositoryService;
    private final JpaSupportService jpaSupportService;
    private final AuthorRepository authorRepository;

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public Author create(
            final String name) {
        return repositoryService.persistAndFlush(Author.withLastName(name));
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT, promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public List<Author> findByName(
            final String name
    ) {
        return authorRepository.findByLastNameContaining(name);
    }

    @Programmatic
    public Author findByNameExact(final String name) {
        return authorRepository.findByLastName(name);
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(bookmarking = BookmarkPolicy.AS_ROOT)
    public List<Author> listAll() {
        return authorRepository.findAll();
    }


/*    @Programmatic
    public void ping() {
        jpaSupportService.getEntityManager(Author.class).ifSuccess(x -> {
            final TypedQuery<Author> q = x.createQuery("SELECT p FROM Author p ORDER BY p.lastName",
                    Author.class).setMaxResults(1);
            q.getResultList();
        });
    }*/

}
