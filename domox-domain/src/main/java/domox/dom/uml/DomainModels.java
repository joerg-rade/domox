package domox.dom.uml;

import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.NatureOfService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.DomainModels")
@javax.annotation.Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class DomainModels {

    private final RepositoryService repositoryService;

    @ActionLayout(sequence = "1")
    public List<DomainModel> listAll() {
        return repositoryService.allInstances(DomainModel.class);
    }

    @ActionLayout(sequence = "2")
    public DomainModel create() {
        final DomainModel obj = new DomainModel();
        repositoryService.persist(obj);
        return obj;
    }

    public String generateUmlDiagram(DomainModel domainModel) {
        return "@startuml \n @endUml";
    }

}