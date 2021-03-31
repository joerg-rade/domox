package domox.svc;

import domox.dom.rqm.Document;

public class NlpAdapter {

    public static void parseTextAndAmend(Document document) {
        //IMPROVE select different implementation, depending on config values
        // default: StanfordNlp via lib
        // StanfordNlpRemote (docker image)
        // ApacheOpenNlpRemote (docker image)
        new StanfordNLP().process(document);
    }
}
