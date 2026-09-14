package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".PropertyCandidates")
@Priority(PriorityPrecedence.EARLY)
public class PropertyCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final PropertyCddRepository propertyCddRepository;
    private final ClassCandidates classCandidates;

    @Inject
    public PropertyCandidates(
            RepositoryService repositoryService,
            FactoryService factoryService,
            PropertyCddRepository propertyCddRepository,
            ClassCandidates classCandidates) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.propertyCddRepository = propertyCddRepository;
        this.classCandidates = classCandidates;
    }

    @ActionLayout(sequence = "1")
    public List<PropertyCdd> listAll() {
        return repositoryService.allInstances(PropertyCdd.class);
    }

    @ActionLayout(sequence = "2")
    public PropertyCdd findByClassAndName(String className, String propertyName) {
        ClassCdd classCdd = classCandidates.findByCandidateName(className);
        if (classCdd == null) {
            return null;
        }
        return propertyCddRepository.findByClassCddAndCandidateName(classCdd, propertyName);
    }

    @ActionLayout(sequence = "3")
    public PropertyCdd create(String className, String propertyName, String type) {
        return create(className, propertyName, type, null);
    }

    @Programmatic
    public PropertyCdd create(String className, String propertyName, String type, DomainModel domainModel) {
        final PropertyCdd obj = factoryService.detachedEntity(PropertyCdd.class);
        obj.setCandidateName(propertyName);
        obj.setCandidateType("PropertyCdd");
        obj.type = type;

        // Retrieve the ClassCdd and set the relationship
        ClassCdd classCdd = classCandidates.findOrCreate(className, domainModel);
        obj.classCdd = classCdd;
        if (classCdd != null) {
            classCdd.propertyList.add(obj);
        }

        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public PropertyCdd findOrCreate(final String className, final String propertyName, final String type) {
        return findOrCreate(className, propertyName, type, null);
    }

    @Programmatic
    public PropertyCdd findOrCreate(final String className, final String propertyName, final String type, DomainModel domainModel) {
        PropertyCdd candidate = findByClassAndName(className, propertyName);
        if (candidate == null) {
            // Ensure the ClassCdd exists
            ClassCdd classCdd = classCandidates.findOrCreate(className, domainModel);
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

    @Action()
    @ActionLayout(sequence = "4", cssClassFa = "trash")
    public void deleteAll() {
        var all = listAll();
        for (PropertyCdd pc : all) {
            repositoryService.remove(pc);
        }
    }
}