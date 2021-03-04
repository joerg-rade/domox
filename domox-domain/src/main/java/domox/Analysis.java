package domox;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;

import javax.inject.Inject;
import java.util.List;

@DomainService(nature = NatureOfService.VIEW)
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class Analysis {
    private final RepositoryService repositoryService;
    private final Authors authors;

    @Action()
    @ActionLayout(cssClassFa = "play")
    public List<Author> init() {
        return authors.listAll();
    }

}