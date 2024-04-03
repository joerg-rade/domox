package domox.dom.nlp;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named("domox.ModelDependencies")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class TypedDependencies {

    private final RepositoryService repositoryService;
    private final TypedDependencyRepository repository;
    private final FactoryService factoryService;

    @ActionLayout(sequence = "1")
    public List<TypedDependency> listAll() {
        return repositoryService.allInstances(TypedDependency.class);
    }

    @ActionLayout(sequence = "2")
    public TypedDependency create() {
        final TypedDependency obj = factoryService.detachedEntity(TypedDependency.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    public List<TypedDependency> findByType(final TdType type) {
        return repository.findByType(type);
    }

}