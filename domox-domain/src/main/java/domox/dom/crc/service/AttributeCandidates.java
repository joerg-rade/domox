package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.repository.AttributeCddRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".AttributeCandidates")
@Priority(PriorityPrecedence.EARLY)
public class AttributeCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final AttributeCddRepository propertyCddRepository;
    private final ClassCandidates classCandidates;

    @Inject
    public AttributeCandidates(
            RepositoryService repositoryService,
            FactoryService factoryService,
            AttributeCddRepository propertyCddRepository,
            ClassCandidates classCandidates) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.propertyCddRepository = propertyCddRepository;
        this.classCandidates = classCandidates;
    }

    // Internal helper (exposed in the UI via the "Candidates" menu service).
    @Programmatic
    public List<AttributeCdd> listAll() {
        return repositoryService.allInstances(AttributeCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    /**
     * Scoped lookup: resolves the owning {@link ClassCdd} within
     * {@code domainModel} (falling back to the global lookup when it is
     * {@code null}) and returns the property that belongs to it.
     */
    @Programmatic
    public AttributeCdd findByClassAndName(String className, String propertyName, DomainModel domainModel) {
        ClassCdd classCdd = classCandidates.findByCandidateName(className, domainModel);
        if (classCdd == null) {
            return null;
        }
        return propertyCddRepository.findByClassCddAndCandidateName(classCdd, propertyName);
    }

    @Programmatic
    public AttributeCdd create(String className, String propertyName, String type) {
        return create(className, propertyName, type, null);
    }

    @Programmatic
    public AttributeCdd create(String className, String propertyName, String type, DomainModel domainModel) {
        final AttributeCdd obj = factoryService.detachedEntity(AttributeCdd.class);
        obj.setCandidateName(propertyName);
        obj.setCandidateType("PropertyCdd");
        obj.type = type;

        // Retrieve the ClassCdd and set the relationship
        ClassCdd classCdd = classCandidates.findOrCreate(className, domainModel);
        if (classCdd == null) {
            // Owner class is blocked (e.g., use-case meta noun) or otherwise unavailable;
            // don't create an orphan property that would violate the FK constraint.
            return null;
        }
        obj.classCdd = classCdd;
        obj.domainModel = domainModel;
        classCdd.attributeList.add(obj);
        if (domainModel != null) {
            domainModel.attributeList.add(obj);
        }

        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public AttributeCdd findOrCreate(final String className, final String propertyName, final String type) {
        return findOrCreate(className, propertyName, type, null);
    }

    @Programmatic
    public AttributeCdd findOrCreate(final String className, final String propertyName, final String type, DomainModel domainModel) {
        AttributeCdd candidate = findByClassAndName(className, propertyName, domainModel);
        if (candidate == null) {
            candidate = create(className, propertyName, type, domainModel);
        } else {
            // Update the type if it has changed
            if (!type.equals(candidate.type)) {
                candidate.type = type;
                repositoryService.persist(candidate);
            }
        }
        return candidate;
    }

}