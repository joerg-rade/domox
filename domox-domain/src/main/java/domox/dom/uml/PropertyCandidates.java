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
        ClassCdd classCdd = classCandidates.findByName(className);
        if (classCdd == null) {
            return null;
        }
        return propertyCddRepository.findByClassCddAndName(classCdd, propertyName);
    }

    @ActionLayout(sequence = "3")
    public PropertyCdd create(String className, String propertyName, String type) {
        final PropertyCdd obj = factoryService.detachedEntity(PropertyCdd.class);
        obj.name = propertyName;
        obj.type = type;

        // Retrieve the ClassCdd and set the relationship
        ClassCdd classCdd = classCandidates.findOrCreate(className);
        obj.classCdd = classCdd;

        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public PropertyCdd findOrCreate(final String className, final String propertyName, final String type) {
        PropertyCdd candidate = findByClassAndName(className, propertyName);
        if (candidate == null) {
            // Ensure the ClassCdd exists
            ClassCdd classCdd = classCandidates.findOrCreate(className);
            candidate = create(className, propertyName, type);
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