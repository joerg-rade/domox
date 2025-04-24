package domox.nlp;

import domox.Constants;
import domox.StanfordCoreNlpTO;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(value = SpringExtension.class)
public class StanfordCoreNlpAPITest {

    @Test
    public void annotateTest() throws Exception {
        val scheme = Constants.coreNlpScheme;
        val host = Constants.coreNlpHost;
        val port = Constants.coreNlpPort;
        val nlp = new StanfordCoreNlpAPI(scheme, host, port);
        final StanfordCoreNlpTO to = nlp.annotate(SampleText.QBF);
    }

}