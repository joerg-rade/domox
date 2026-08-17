package domox.dom.uml;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".ClassCandidates")
@Priority(PriorityPrecedence.EARLY)
public class ClassCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ClassCddRepository classCddRepository;

    @Inject
    public ClassCandidates(RepositoryService repositoryService, FactoryService factoryService, ClassCddRepository classCddRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.classCddRepository = classCddRepository;
    }

    @ActionLayout(sequence = "1")
    public List<ClassCdd> listAll() {
        return repositoryService.allInstances(ClassCdd.class);
    }

    @ActionLayout(sequence = "2")
    public ClassCdd findByName(String candidateName) {
        return classCddRepository.findByName(candidateName);
    }

    @ActionLayout(sequence = "3")
    public ClassCdd create(String candidateName) {
        final ClassCdd obj = factoryService.detachedEntity(ClassCdd.class);
        obj.name = candidateName;
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public ClassCdd findOrCreate(final String candidateName) {
        ClassCdd candidate = findByName(candidateName);
        if (candidate == null) {
            candidate = create(candidateName);
        }
        return candidate;
    }
}
