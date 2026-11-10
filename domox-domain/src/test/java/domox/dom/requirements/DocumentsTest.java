package domox.dom.requirements;

import domox.port.DiagramPort;
import domox.dom.nlp.Sentences;
import domox.dom.rules.RuleMatches;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentsTest {

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    Sentences sentences;
    @Mock
    DiagramPort mockDiagramPort;
    @Mock
    MessageService mockMessageService;
    @Mock
    RuleMatches ruleMatches;

    // ClassUnderTest
    Documents documents;
    @BeforeEach
    public void setUp() {
        documents = new Documents(mockRepositoryService, sentences, mockDiagramPort, mockMessageService, ruleMatches);
    }

    @Test
    void existsByContent_detectsDocumentsWithMatchingContent() {
        // given — an already-analysed document
        final String text = "A pet shop can offer a wide range of pet products.";
        final Document existing = new Document();
        existing.setContent(text);
        when(mockRepositoryService.allInstances(Document.class)).thenReturn(List.of(existing));

        // expect — exact content match is detected, other/null content is not
        assertThat(documents.existsByContent(text)).isTrue();
        assertThat(documents.existsByContent("Some unrelated requirements text.")).isFalse();
        assertThat(documents.existsByContent(null)).isFalse();
    }
}
