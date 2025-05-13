package domox.svc;

import domox.Constants;
import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;
import lombok.val;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

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
        Assert.assertNotNull(documentTO);
        Assert.assertNotNull(documentTO.getSentences());
        Assert.assertTrue(documentTO.getSentences().size() > 0);
        Assert.assertNotNull(documentTO.getSentences().get(0));

        val sentence = documentTO.getSentences().get(0);
        Assert.assertNotNull(sentence);
    }

}