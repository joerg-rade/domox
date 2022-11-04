package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.NatureOfService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.ModelDependencies")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class TypedDependencies {

    private final RepositoryService repositoryService;
    private final TypedDependencyRepository repository;
    private final FactoryService factoryService;

    @PropertyLayout(sequence = "1")
    public List<TypedDependency> listAll() {
        return repositoryService.allInstances(TypedDependency.class);
    }

    @PropertyLayout(sequence = "2")
    public TypedDependency create() {
        final TypedDependency obj = factoryService.detachedEntity(TypedDependency.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @PropertyLayout(sequence = "3")
    public List<TypedDependency> findByType(final TdType type) {
        return repository.findByType(type);
    }

}