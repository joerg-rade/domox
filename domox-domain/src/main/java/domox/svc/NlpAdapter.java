package domox.svc;

import domox.HttpRequest;
import domox.SparkNlpAPI;
import domox.StanfordCoreNlpAPI;
import domox.TdDiagram;
import domox.dom.nlp.Sentence;
import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.TypedDependency;
import org.apache.causeway.applib.value.Blob;

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
//        stanfordServer(dmxDoc);
    }

    private static void stanfordServer(Document dmxDoc) {
        final String rawText = (String) dmxDoc.getContent();
//        final StanfordCoreNlpServer nlp = new StanfordCoreNlpServer();
//        final Annotation annotation = nlp.annotate(rawText);
     /*   annotation.get(CoreSentence.class);
        for (CoreSentence cs : csList) {
            final String sentenceText = cs.text();
            System.out.println(sentenceText);
            final Sentence dmxSentence = new Sentence();
            dmxSentence.setText(sentenceText);
            dmxSentence.setTypedDependencies(typedDependenciesAsList(cs).toString());
            dmxSentence.setDiagram(buildTypedDependencyDiagram(cs));
            dmxDoc.getSentences().add(dmxSentence);
        }*/
    }

    private static void sparkNlpServer(Document dmxDoc) {
        final String rawText = dmxDoc.getContent();
        final SparkNlpAPI nlp = new SparkNlpAPI();
        nlp.annotate(rawText);
/*        final CoreDocument coreDocument = nlp.annotate(rawText);
        final List<CoreSentence> csList = coreDocument.sentences();
        for (CoreSentence cs : csList) {
            final String sentenceText = cs.text();
            System.out.println(sentenceText);
            final Sentence dmxSentence = new Sentence();
            dmxSentence.setText(sentenceText);
            dmxSentence.setTypedDependencies(typedDependenciesAsList(cs).toString());
            dmxSentence.setDiagram(buildTypedDependencyDiagram(cs));
            dmxDoc.getSentences().add(dmxSentence);
        } */
    }

    private static void stanfordLib(Document dmxDoc) {
        final String rawText = dmxDoc.getContent();
        final StanfordCoreNlpAPI nlp = new StanfordCoreNlpAPI();
        final CoreDocument coreDocument = nlp.annotate(rawText);
        final List<CoreSentence> csList = coreDocument.sentences();
        for (CoreSentence cs : csList) {
            final String sentenceText = cs.text();
            System.out.println(sentenceText);
            final Sentence dmxSentence = new Sentence();
            dmxSentence.setText(sentenceText);
            dmxSentence.setTypedDependencies(typedDependenciesAsList(cs).toString());
            dmxSentence.setDiagram(buildTypedDependencyDiagram(cs));
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

    public static Blob buildTypedDependencyDiagram(CoreSentence coreSentence) {
        final String pumlCode = TdDiagram.INSTANCE.build(coreSentence, true);
        final String svg = new HttpRequest().invokePlantUML(pumlCode);
        final String clean = svg.replaceAll("transparent", "white");
        return new Blob("SVG Diagram", "image/jepg", clean.getBytes());
    }

}
