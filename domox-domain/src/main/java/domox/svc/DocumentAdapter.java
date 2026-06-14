package domox.svc;

import domox.Constants;
import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;

public class DocumentAdapter {

    public DocumentTO parseTextAndAmend(String rawText) {
        final String scheme = getSystemProperty("corenlp.scheme", Constants.coreNlpScheme);
        final String host = getSystemProperty("corenlp.host", Constants.coreNlpHost);
        final int port = Integer.parseInt(getSystemProperty("corenlp.port", String.valueOf(Constants.coreNlpPort)));
        final StanfordCoreNlpAPI nlp = new StanfordCoreNlpAPI(scheme, host, port);
        return nlp.annotate(rawText);
    }

    private static String getSystemProperty(String key, String defaultValue) {
        final String value = System.getProperty(key);
        return value != null ? value : defaultValue;
    }

}
