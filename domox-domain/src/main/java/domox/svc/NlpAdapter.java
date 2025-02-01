package domox.svc;

import domox.HttpRequest;
import domox.StanfordCoreNlpAPI;
import domox.TdDiagram;
import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.TypedDependency;
import org.apache.causeway.applib.value.Blob;

import java.util.ArrayList;
import java.util.Iterator;

public class NlpAdapter {

    public static void parseTextAndAmend(Document dmxDoc) {
        stanfordServer(dmxDoc);
    }

    private static void stanfordServer(Document dmxDoc) {
        final String rawText = (String) dmxDoc.getContent();
        final String scheme = "http";
        final String host = "localhost";
        final int port = 8090;
        final StanfordCoreNlpAPI nlp = new StanfordCoreNlpAPI(scheme, host, port);
       // final Annotation annotation =
                nlp.annotate(rawText);
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
