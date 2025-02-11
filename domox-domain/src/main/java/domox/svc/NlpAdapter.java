package domox.svc;

import domox.HttpRequest;
import domox.StanfordCoreNlpAPI;
import domox.StanfordCoreNlpTO;
import domox.TdDiagram;
import domox.dom.rqm.Document;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.semgraph.SemanticGraph;
import org.apache.causeway.applib.value.Blob;

import java.util.ArrayList;

public class NlpAdapter {

    public StanfordCoreNlpTO parseTextAndAmend(Document dmxDoc) {
        final String rawText = dmxDoc.getContent();
        final String scheme = "http";
        final String host = "localhost";
        final int port = 8090;
        final StanfordCoreNlpAPI nlp = new StanfordCoreNlpAPI(scheme, host, port);
        return nlp.annotate(rawText);
    }

    private ArrayList<String> typedDependenciesAsList(CoreSentence coreSentence) {
        final ArrayList<String> answer = new ArrayList<>();
        final SemanticGraph dependencyParse = coreSentence.dependencyParse();
/*        Iterator<TypedDependency> dependencyIterator = dependencyParse.typedDependencies().iterator();
        while (dependencyIterator.hasNext()) {
            TypedDependency dependency = dependencyIterator.next();
            answer.add(dependency.reln().toString());
        } */
        return answer;
    }

    public Blob buildTypedDependencyDiagram(CoreSentence coreSentence) {
        final String pumlCode = TdDiagram.INSTANCE.build(coreSentence, true);
        final String svg = new HttpRequest().invokePlantUML(pumlCode);
        final String clean = svg.replaceAll("transparent", "white");
        return new Blob("SVG Diagram", "image/jepg", clean.getBytes());
    }

}
