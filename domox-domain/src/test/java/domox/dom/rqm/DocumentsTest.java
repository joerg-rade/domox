package domox.dom.rqm;

import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.value.Clob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.isis.commons.internal.assertions._Assert.assertEquals;

@ExtendWith(MockitoExtension.class)
class DocumentsTest {

    @Mock
    RepositoryService mockRepositoryService;

    Documents documents;

    @BeforeEach
    public void setUp() {
        documents = new Documents(mockRepositoryService);
    }

    @Test
    void newDocument() {
        // given
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
        // then
        assertEquals(1, documents.listAll().size());
//        assertEquals("Skinner", document.getAuthors().stream().toArray().g . (1).getL);
    }
}