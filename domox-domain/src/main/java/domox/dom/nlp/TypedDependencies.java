package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PriorityPrecedence;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.factory.FactoryService;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.services.JpaSupportService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.ModelDependencies")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@lombok.RequiredArgsConstructor(onConstructor_ = {@Inject} )
public class TypedDependencies {

    private final RepositoryService repositoryService;
    final JpaSupportService jpaSupportService;
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