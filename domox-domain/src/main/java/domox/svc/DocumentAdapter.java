package domox.svc;

import domox.Constants;
import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;

public class DocumentAdapter {

    public DocumentTO parseTextAndAmend(String rawText) {
        final String scheme = Constants.coreNlpScheme;
        final String host = Constants.coreNlpHost;
        final int port = Constants.coreNlpPort;
        final StanfordCoreNlpAPI nlp = new StanfordCoreNlpAPI(scheme, host, port);
        return nlp.annotate(rawText);
    }

}
