package domox.svc;

import domox.Constants;
import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;
import lombok.val;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class DocumentAdapterTest {

    @Test
    void typedDependenciesAsList() {
        //given
        val scheme = Constants.coreNlpScheme;
        val host = Constants.coreNlpHost;
        val port = Constants.coreNlpPort;
        val nlpAPI = new StanfordCoreNlpAPI(scheme, host, port);
        val qbf = "The quick brown fox jumped over the lazy dog.";
        final DocumentTO to = nlpAPI.annotate(qbf);
        Assert.assertNotNull(to);
        Assert.assertNotNull(to.getSentences());
        Assert.assertTrue(to.getSentences().size() > 0);
        Assert.assertNotNull(to.getSentences().get(0));

        val sentence = to.getSentences().get(0);
        val nlp = new DocumentAdapter();
        val td = nlp.typedDependenciesAsList(sentence);
        Assert.assertNotNull(td);
    }

}