package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".ActionCandidates")
@Priority(PriorityPrecedence.EARLY)
public class ActionCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ActionCddRepository actionCddRepository;

    @Inject
    public ActionCandidates(RepositoryService repositoryService, FactoryService factoryService, ActionCddRepository actionCddRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.actionCddRepository = actionCddRepository;
    }

    // Internal helper (exposed in the UI via the "Candidates" menu service).
    @Programmatic
    public List<ActionCdd> listAll() {
        return repositoryService.allInstances(ActionCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @Programmatic
    public ActionCdd findByCandidateName(String candidateName) {
        return actionCddRepository.findByCandidateName(candidateName);
    }

    @Programmatic
    public ActionCdd create(String candidateName) {
        return create(candidateName, null);
    }

    @Programmatic
    public ActionCdd create(String candidateName, DomainModel domainModel) {
        return create(candidateName, null, domainModel);
    }

    @Programmatic
    public ActionCdd create(String candidateName, ClassCdd classCdd, DomainModel domainModel) {
        final ActionCdd obj = factoryService.detachedEntity(ActionCdd.class);
        obj.setCandidateName(candidateName);
        obj.setCandidateType("ActionCdd");
        obj.setOutputType("void");
        obj.classCdd = classCdd;
        obj.domainModel = domainModel;
        if (classCdd != null) {
            classCdd.actionList.add(obj);
        }
        if (domainModel != null) {
            domainModel.actionList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public ActionCdd findByCandidateName(String candidateName, DomainModel domainModel) {
        if (domainModel == null) {
            return findByCandidateName(candidateName);
        }
        return actionCddRepository.findByCandidateNameAndDomainModel(candidateName, domainModel);
    }

    @Programmatic
    public ActionCdd findOrCreate(final String candidateName) {
        return findOrCreate(candidateName, null, null);
    }

    @Programmatic
    public ActionCdd findOrCreate(final String candidateName, final DomainModel domainModel) {
        return findOrCreate(candidateName, null, domainModel);
    }

    @Programmatic
    public ActionCdd findOrCreate(final String candidateName, final ClassCdd classCdd, final DomainModel domainModel) {
        ActionCdd candidate = findByCandidateName(candidateName, domainModel);
        if (candidate == null) {
            candidate = create(candidateName, classCdd, domainModel);
        }
        return candidate;
    }

}