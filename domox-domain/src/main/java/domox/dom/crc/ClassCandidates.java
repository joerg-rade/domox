package domox.dom.crc;

import domox.DomainModule;
import domox.dom.nlp.TypedDependency;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
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
    public ClassCdd findByCandidateName(String candidateName) {
        return classCddRepository.findByCandidateName(candidateName);
    }

    @ActionLayout(sequence = "3")
    public ClassCdd create(String candidateName) {
        // Auto-create a DomainModel for UI convenience
        final DomainModel domainModel = new DomainModel();
        repositoryService.persist(domainModel);
        return create(candidateName, domainModel);
    }

    @Programmatic
    public ClassCdd create(String candidateName, DomainModel domainModel) {
        final ClassCdd obj = factoryService.detachedEntity(ClassCdd.class);
        obj.setCandidateName(candidateName);
        obj.setCandidateType("ClassCdd");
        obj.domainModel = domainModel;
        if (domainModel != null) {
            domainModel.classList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public ClassCdd findOrCreate(final String candidateName, final DomainModel domainModel) {
        ClassCdd candidate = findByCandidateName(candidateName);
        if (candidate == null) {
            if (domainModel == null) {
                candidate = create(candidateName);
            } else {
                candidate = create(candidateName, domainModel);
            }
        }
        return candidate;
    }

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "trash")
    public void deleteAll() {
        var all = listAll();
        for (ClassCdd cc : all) {
            repositoryService.remove(cc);
        }
    }
}
