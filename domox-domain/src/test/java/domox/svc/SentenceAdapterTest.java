package domox.svc;

import domox.FileUtil;
import domox.nlp.DocumentTO;
import domox.nlp.SentenceTO;
import org.apache.causeway.applib.value.Blob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SentenceAdapterTest {

    @Test
    void buildTypedDependencyDiagram() {
        final String filename = "PetShop_useCases.txt";
        final String txtContent = new FileUtil().readFileFromResources(filename);
        final DocumentTO documentTO = new DocumentAdapter().parseTextAndAmend(txtContent);
        final SentenceTO sentenceTO = documentTO.getSentences().get(0);
        final SentenceAdapter sut = new SentenceAdapter(sentenceTO);
        // when
        final Blob diagram = sut.buildTypedDependencyDiagram();
        // then
        assertNotNull(diagram);
    }
}