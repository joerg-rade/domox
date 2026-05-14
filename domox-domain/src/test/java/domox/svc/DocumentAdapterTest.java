package domox.svc;

import domox.Constants;
import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Requires CoreNLP service to be running")
class DocumentAdapterTest {

    @Test
    void annotateTest() {
        //given
        val scheme = Constants.coreNlpScheme;
        val host = Constants.coreNlpHost;
        val port = Constants.coreNlpPort;
        val nlpAPI = new StanfordCoreNlpAPI(scheme, host, port);
        val qbf = "The quick brown fox jumped over the lazy dog.";
        final DocumentTO documentTO = nlpAPI.annotate(qbf);
        assertNotNull(documentTO);
        assertNotNull(documentTO.getSentences());
        assertTrue(documentTO.getSentences().size() > 0);
        assertNotNull(documentTO.getSentences().getFirst());

        val sentence = documentTO.getSentences().getFirst();
        assertNotNull(sentence);
    }

}
