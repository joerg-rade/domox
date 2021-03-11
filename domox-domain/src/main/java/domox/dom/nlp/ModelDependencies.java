package domox.dom.nlp;

import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "domox.ModelDependencies")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class ModelDependencies {
    private final RepositoryService repositoryService;
    private final ModelDependencyRepository repository;

    @MemberOrder(sequence = "1")
    public List<ModelDependency> listAll() {
        return repositoryService.allInstances(ModelDependency.class);
    }

    @MemberOrder(sequence = "2")
    public ModelDependency create() {
        final ModelDependency obj = repositoryService.detachedEntity(ModelDependency.class);
//        obj.setTitle(title);
        repositoryService.persist(obj);
        return obj;
    }

    @MemberOrder(sequence = "3")
    public List<ModelDependency> findByType(final ModelType type) {
        return repository.findByType(type);
    }

}