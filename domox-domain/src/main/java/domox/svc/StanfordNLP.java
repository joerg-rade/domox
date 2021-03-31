package domox.svc;

import domox.dom.nlp.Sentence;
import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.TypedDependency;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class StanfordNLP {

    public StanfordNLP() {
        init();
    }

    private StanfordCoreNLP pipeline;

    private void init() {
        try {
            Properties props = new Properties();
//            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref");
            props.setProperty("coref.algorithm", "neural");
            pipeline = new StanfordCoreNLP(props);
        } catch (Exception e) {
            throw new RuntimeException("Exception occured in creating instance");
        }
    }

    public void process(Document dmxDoc) {
        final String text = (String) dmxDoc.getContent().getChars();
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);

        final List<CoreSentence> csList = document.sentences();
        for (CoreSentence cs : csList) {
            final String sentenceText = cs.text();
            System.out.println(sentenceText);
            final Sentence dmxSentence = new Sentence();
            dmxSentence.setRaw(sentenceText);
            dmxSentence.setTypedDependencies(typedDependenciesAsList(cs).toString());
            dmxDoc.getSentences().add(dmxSentence);
        }
    }

    private ArrayList<String> typedDependenciesAsList(CoreSentence coreSentence) {
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
