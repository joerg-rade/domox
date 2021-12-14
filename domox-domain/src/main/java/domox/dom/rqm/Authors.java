package domox.dom.rqm;

import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.services.JpaSupportService;

import javax.inject.Inject;
import javax.persistence.TypedQuery;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.Authors"
)
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject} )
public class Authors {

    private RepositoryService repositoryService;
    private JpaSupportService jpaSupportService;
    private AuthorRepository authorRepository;

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


    @Programmatic
    public void ping() {
        jpaSupportService.getEntityManager(Author.class).ifSuccess(x -> {
            final TypedQuery<Author> q = x.createQuery("SELECT p FROM Author p ORDER BY p.lastName",
                    Author.class).setMaxResults(1);
            q.getResultList();
        });
    }

}
