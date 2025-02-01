package domox;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(value = SpringExtension.class)
public class CoreNlpTest {

    @Test
    public void annotateTest() throws Exception {
        val scheme = "http";
        val host = "localhost";
        val port = 8090;
        val nlp = new StanfordCoreNlpAPI(scheme, host, port);
        final StanfordCoreNlpTO to = nlp.annotate("The quick brown fox jumped over the lazy dog");
    }

}