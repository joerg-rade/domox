package domox.dom.rqm;

import domox.diagram.DiagramBuilder;
import domox.dom.nlp.Sentences;
import domox.dom.rules.RuleMatches;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.value.Clob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentsTest {

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    Sentences sentences;
    @Mock
    DiagramBuilder mockDiagramBuilder;
    @Mock
    MessageService mockMessageService;
    @Mock
    RuleMatches ruleMatches;

    // ClassUnderTest
    Documents documents;
    @BeforeEach
    public void setUp() {
        documents = new Documents(mockRepositoryService, sentences, mockDiagramBuilder, mockMessageService, ruleMatches);
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
        final List<Author> authors = new ArrayList<>();
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

    @Test
    void existsByContent_detectsDocumentsWithMatchingContent() {
        // given — an already-analysed document
        final String text = "A pet shop can offer a wide range of pet products.";
        final Document existing = new Document();
        existing.setContent(text);
        when(mockRepositoryService.allInstances(Document.class)).thenReturn(List.of(existing));

        // expect — exact content match is detected, other/null content is not
        assertTrue(documents.existsByContent(text));
        assertFalse(documents.existsByContent("Some unrelated requirements text."));
        assertFalse(documents.existsByContent(null));
    }
}
