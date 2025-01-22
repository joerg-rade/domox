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
import org.apache.causeway.applib.annotation.PromptStyle;
import org.apache.causeway.applib.annotation.SemanticsOf;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;
import java.util.stream.Collectors;

@Named(DomainModule.NAMESPACE + ".Authors")
@DomainService
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject} )
public class Authors {

    private final RepositoryService repositoryService;

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public Author create(
            final String name) {
        return repositoryService.persistAndFlush(Author.withLastName(name));
    }

    @Action(semantics = SemanticsOf.SAFE)
    @ActionLayout(promptStyle = PromptStyle.DIALOG_SIDEBAR)
    public List<Author> findByName(final String name) {
        final List<Author> all = this.listAll();
        final List<Author> result = all.stream()
                .filter(item -> item.getLastName().equals(name))
                .collect(Collectors.toList());
        return result;
    }

    @Action(semantics = SemanticsOf.SAFE)
    //@ActionLayout()
    public List<Author> listAll() {
        return repositoryService.allInstances(Author.class);
    }

}
