package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.repository.CollaboratorCddRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".CollaboratorCandidates")
@Priority(PriorityPrecedence.EARLY)
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class CollaboratorCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final CollaboratorCddRepository associationCddRepository;

    @Inject
    public CollaboratorCandidates(
            RepositoryService repositoryService,
            FactoryService factoryService,
            CollaboratorCddRepository associationCddRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.associationCddRepository = associationCddRepository;
    }

    // Internal helper (exposed in the UI via the "Candidates" menu service).
    @Programmatic
    public List<CollaboratorCdd> listAll() {
        return repositoryService.allInstances(CollaboratorCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @Programmatic
    public CollaboratorCdd findByCandidateName(String candidateName) {
        return associationCddRepository.findByCandidateName(candidateName);
    }

    @Programmatic
    public CollaboratorCdd findByCandidateName(String candidateName, DomainModel domainModel) {
        if (domainModel == null) {
            return findByCandidateName(candidateName);
        }
        return associationCddRepository.findByCandidateNameAndDomainModel(candidateName, domainModel);
    }

    @Programmatic
    public CollaboratorCdd create(String associationName, ClassCdd source, ClassCdd target) {
        return create(associationName, source, target, null);
    }

    @Programmatic
    public CollaboratorCdd create(String associationName, ClassCdd source, ClassCdd target, DomainModel domainModel) {
        return create(associationName, source, target, domainModel, AssociationType.ASSOCIATION);
    }

    @Programmatic
    public CollaboratorCdd create(
            String associationName, ClassCdd source, ClassCdd target,
            DomainModel domainModel, AssociationType type) {
        final CollaboratorCdd obj = factoryService.detachedEntity(CollaboratorCdd.class);
        obj.setCandidateName(associationName);
        obj.setCandidateType("AssociationCdd");
        obj.setClassCdd(source);
        obj.setDomainModel(domainModel);
        obj.setSource(source);
        obj.setTarget(target);
        obj.setType(type != null ? type : AssociationType.ASSOCIATION);

        source.addCollaborator(obj);
        if (domainModel != null) {
            domainModel.collaboratorList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public CollaboratorCdd findOrCreate(
            final String associationName,
            final ClassCdd source,
            final ClassCdd target,
            final DomainModel domainModel) {
        return findOrCreate(associationName, source, target, domainModel, AssociationType.ASSOCIATION);
    }

    @Programmatic
    public CollaboratorCdd findOrCreate(
            final String associationName,
            final ClassCdd source,
            final ClassCdd target,
            final DomainModel domainModel,
            final AssociationType type) {
        CollaboratorCdd candidate = findByCandidateName(associationName, domainModel);
        if (candidate == null) {
            candidate = create(associationName, source, target, domainModel, type);
        }
        return candidate;
    }

}