package domox.dom.uml;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.NatureOfService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.DomainModels")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class DomainModels {

    private final RepositoryService repositoryService;
    private final DomainModelRepository domainModelRepository;

    @ActionLayout(sequence = "1")
    public List<DomainModel> listAll() {
        return domainModelRepository.findAll();
    }

    @ActionLayout(sequence = "2")
    public DomainModel create() {
        final DomainModel obj = new DomainModel();
        repositoryService.persist(obj);
        return obj;
    }


    public String generateUmlDiagram(DomainModel domainModel) {
        return "@startuml\n@enduml";
    }

}