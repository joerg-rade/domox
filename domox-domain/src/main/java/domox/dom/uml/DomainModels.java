package domox.dom.uml;

import domox.DomainModule;
import generate.ClassDiagram;
import generate.JavaCode;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".DomainModels")
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
        return new ClassDiagram().build(domainModel);
    }
    public String generateJavaCode(ClassCdd clazz) {
        return new JavaCode(clazz).getCode();
    }

}