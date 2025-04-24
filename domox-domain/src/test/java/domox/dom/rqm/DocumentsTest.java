package domox.dom.rqm;

import domox.dom.nlp.Sentences;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;

@Ignore
class DocumentsTest {

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    Sentences sentences;

    // ClassUnderTest
    Documents documents;
    @BeforeEach
    public void setUp() {
        documents = new Documents(mockRepositoryService, sentences);
    }

 //   @Test
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

        final Document o = new Document();
        o.setContent(content.asString());
        o.setUrl(url);
        o.setTitle(title);
        o.setAuthors(authors);
        final List<Document> list = new ArrayList<>();
        list.add(o);
/*        context.checking(new Expectations() {
            {
                allowing(mockRepositoryService).detachedEntity(Document.class);
                will(returnValue(o));

                allowing(mockRepositoryService).persist(o);

                allowing(mockRepositoryService).allInstances(Document.class);
                will(returnValue(list));
            }
        });*/

        // when
        final Document document = documents.create(title, url, content, authors);
        // then
        assertEquals(1, documents.listAll().size());
        assertEquals("Skinner", Arrays.stream(document.getAuthors().stream().toArray()).findFirst());
    }
}