package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.repository.ResponsibilityCddRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".ResponsibilityCandidates")
@Priority(PriorityPrecedence.EARLY)
public class ResponsibilityCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ResponsibilityCddRepository responsibilityCddRepository;

    @Inject
    public ResponsibilityCandidates(RepositoryService repositoryService, FactoryService factoryService, ResponsibilityCddRepository responsibilityCddRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.responsibilityCddRepository = responsibilityCddRepository;
    }

    // Internal helper (exposed in the UI via the "Candidates" menu service).
    @Programmatic
    public List<ResponsibilityCdd> listAll() {
        return repositoryService.allInstances(ResponsibilityCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @Programmatic
    public ResponsibilityCdd findByCandidateName(String candidateName) {
        return responsibilityCddRepository.findByCandidateName(candidateName);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName) {
        return create(candidateName, null);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName, DomainModel domainModel) {
        return create(candidateName, null, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName, ClassCdd classCdd, DomainModel domainModel) {
        final ResponsibilityCdd obj = factoryService.detachedEntity(ResponsibilityCdd.class);
        obj.setCandidateName(candidateName);
        obj.setCandidateType("ActionCdd");
        obj.setOutputType("void");
        obj.classCdd = classCdd;
        obj.domainModel = domainModel;
        if (classCdd != null) {
            classCdd.responsibilityList.add(obj);
        }
        if (domainModel != null) {
            domainModel.responsibilityList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public ResponsibilityCdd findByCandidateName(String candidateName, DomainModel domainModel) {
        if (domainModel == null) {
            return findByCandidateName(candidateName);
        }
        return responsibilityCddRepository.findByCandidateNameAndDomainModel(candidateName, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName) {
        return findOrCreate(candidateName, null, null);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName, final DomainModel domainModel) {
        return findOrCreate(candidateName, null, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName, final ClassCdd classCdd, final DomainModel domainModel) {
        ResponsibilityCdd candidate = findByCandidateName(candidateName, domainModel);
        if (candidate == null) {
            candidate = create(candidateName, classCdd, domainModel);
        }
        return candidate;
    }

}