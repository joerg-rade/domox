package domox.dom.nlp;

import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.PriorityPrecedence;
import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.services.factory.FactoryService;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        logicalTypeName = "domox.ModelDependencies")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
public class TypedDependencies {

    @Inject
    private RepositoryService repositoryService;
    //    @Inject private JpaSupportService jpaSupportService;
    @Inject
    private TypedDependencyRepository repository;
    @Inject
    private FactoryService factoryService;

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