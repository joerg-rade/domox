package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Parameter;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".SubDomains")
@Priority(PriorityPrecedence.EARLY)
public class SubDomains {

    private final RepositoryService repositoryService;
    private final SubDomainRepository subDomainRepository;

    @Inject
    public SubDomains(RepositoryService repositoryService, SubDomainRepository subDomainRepository) {
        this.repositoryService = repositoryService;
        this.subDomainRepository = subDomainRepository;
    }

    @ActionLayout(sequence = "1")
    public List<SubDomain> listAll() {
        return repositoryService.allInstances(SubDomain.class);
    }

    @ActionLayout(sequence = "2")
    public SubDomain create(
            @Parameter(optionality = Optionality.MANDATORY)
            final String name) {
        final SubDomain obj = new SubDomain();
        obj.setName(name);
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public SubDomain findByName(final String name) {
        return subDomainRepository.findByName(name);
    }
}