package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".AssociationCandidates")
@Priority(PriorityPrecedence.EARLY)
@DomainServiceLayout(menuBar = DomainServiceLayout.MenuBar.PRIMARY)
public class AssociationCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final AssociationCddRepository associationCddRepository;

    @Inject
    public AssociationCandidates(
            RepositoryService repositoryService,
            FactoryService factoryService,
            AssociationCddRepository associationCddRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.associationCddRepository = associationCddRepository;
    }

    @ActionLayout(sequence = "1")
    public List<AssociationCdd> listAll() {
        return repositoryService.allInstances(AssociationCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @ActionLayout(sequence = "2")
    public AssociationCdd findByCandidateName(String candidateName) {
        return associationCddRepository.findByCandidateName(candidateName);
    }

    @ActionLayout(sequence = "3")
    public AssociationCdd create(String associationName, ClassCdd source, ClassCdd target) {
        return create(associationName, source, target, null);
    }

    @Programmatic
    public AssociationCdd create(String associationName, ClassCdd source, ClassCdd target, DomainModel domainModel) {
        return create(associationName, source, target, domainModel, AssociationType.ASSOCIATION);
    }


    @Programmatic
    public AssociationCdd create(
            String associationName, ClassCdd source, ClassCdd target,
            DomainModel domainModel, AssociationType type) {
        final AssociationCdd obj = factoryService.detachedEntity(AssociationCdd.class);
        obj.setCandidateName(associationName);
        obj.setCandidateType("AssociationCdd");
        obj.setClassCdd(source);
        obj.setSource(source);
        obj.setTarget(target);
        obj.setType(type != null ? type : AssociationType.ASSOCIATION);

        source.addAssociation(obj);
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public AssociationCdd findOrCreate(
            final String associationName,
            final ClassCdd source,
            final ClassCdd target,
            final DomainModel domainModel) {
        return findOrCreate(associationName, source, target, domainModel, AssociationType.ASSOCIATION);
    }


    @Programmatic
    public AssociationCdd findOrCreate(
            final String associationName,
            final ClassCdd source,
            final ClassCdd target,
            final DomainModel domainModel,
            final AssociationType type) {
        AssociationCdd candidate = findByCandidateName(associationName);
        if (candidate == null) {
            candidate = create(associationName, source, target, domainModel, type);
        }
        return candidate;
    }

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "trash")
    public void deleteAll() {
        var all = listAll();
        for (AssociationCdd ac : all) {
            repositoryService.remove(ac);
        }
    }
}