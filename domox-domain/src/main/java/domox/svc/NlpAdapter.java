package domox.svc;

import domox.StanfordNLP;
import domox.dom.nlp.Sentence;
import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.TypedDependency;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NlpAdapter {

    public static void parseTextAndAmend(Document dmxDoc) {
        //IMPROVE select different implementation, depending on config values
        // default: StanfordNlp via lib
        // StanfordNlpRemote (docker image)
        // ApacheOpenNlpRemote (docker image)
        //new StanfordNLP().process(document);
        stanfordLib(dmxDoc);
    }

    private static void stanfordLib(Document dmxDoc) {
        final String rawText = (String) dmxDoc.getContent().getChars();
        final StanfordNLP nlp = new StanfordNLP();
        final CoreDocument coreDocument = nlp.annotate(rawText);
        final List<CoreSentence> csList = coreDocument.sentences();
        for (CoreSentence cs : csList) {
            final String sentenceText = cs.text();
            System.out.println(sentenceText);
            final Sentence dmxSentence = new Sentence();
            dmxSentence.setRaw(sentenceText);
            dmxSentence.setTypedDependencies(typedDependenciesAsList(cs).toString());
            dmxDoc.getSentences().add(dmxSentence);
        }
    }

    private static ArrayList<String> typedDependenciesAsList(CoreSentence coreSentence) {
        final ArrayList<String> answer = new ArrayList<>();
        final SemanticGraph dependencyParse = coreSentence.dependencyParse();
        Iterator<TypedDependency> dependencyIterator = dependencyParse.typedDependencies().iterator();
        while (dependencyIterator.hasNext()) {
            TypedDependency dependency = dependencyIterator.next();
            answer.add(dependency.reln().toString());
        }
        return answer;
    }

}
