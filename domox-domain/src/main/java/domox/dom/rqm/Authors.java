package domox.dom.rqm;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.PromptStyle;
import org.apache.causeway.applib.annotation.SemanticsOf;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@Named(DomainModule.NAMESPACE + ".Authors")
@DomainService
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject} )
public class Authors {

    private final RepositoryService repositoryService;
    private final AuthorRepository authorRepository;

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public Author create(
            final String name) {
        return repositoryService.persistAndFlush(Author.withLastName(name));
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
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
    //@ActionLayout()
    public List<Author> listAll() {
        return authorRepository.findAll();
    }

}
