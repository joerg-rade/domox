package domox;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import domox.dom.rqm.Document;
import domox.dom.rqm.Documents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.value.Clob;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@DomainService(nature = NatureOfService.VIEW ,
        objectType = "domox.Abalysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class Analysis {
    private final RepositoryService repositoryService;
    private final Documents documents;

    @Action()
    @ActionLayout(cssClassFa = "play")
    public List<Document> init() {
        setUpFixtures();
        return documents.listAll();
    }

    @Programmatic
    private void setUpFixtures() {
        final Author holland = Author.withLastName("Holland");
        holland.setFirstName("James");
        holland.setMiddleInitial("G.");
        holland.setEMail("do_not_reply@apa.org");
        final Author skinner = Author.withLastName("Skinner");
        skinner.setFirstName("Burroughs");
        skinner.setMiddleInitial("F.");
        skinner.setEMail("do_not_reply@apa.org");
        final Set<Author> authors = new HashSet<>();
        authors.add(holland);
        authors.add(skinner);
        final String title = "Analysis of Behaviour";
        final String url = "";
        final String mimeTypeBase = "text/xml";
        final Clob content = new Clob("", mimeTypeBase, "");
        // when
        final Document document = documents.create(title, url, content, authors);
    }

}