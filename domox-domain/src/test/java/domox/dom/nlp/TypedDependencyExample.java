package domox.dom.nlp;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

@Slf4j
public class TypedDependencyExample {

    private StanfordCoreNLP pipeline;

    //TODO refactor to use service from module (kotlin)
    //this here is using the java API from the jar !!!
    @BeforeEach
    public void setup() {
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
//        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
        props.setProperty("annotators", "depparse");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        props.setProperty("coref.algorithm", "neural");
        // build pipeline
        pipeline = new StanfordCoreNLP(props);
    }

    @Test
    public void getNounPhrasesTest() {
        final String s = "What is the meaning of life, the universe and everything?";
        ArrayList<String> nounP = new ArrayList<>();

        Annotation annotation = new Annotation(s);
        pipeline.annotate(annotation);

        List<CoreMap> question = annotation.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : question) {
            SemanticGraph basicDeps = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
            Collection<TypedDependency> typedDeps = basicDeps.typedDependencies();

            Iterator<TypedDependency> dependencyIterator = typedDeps.iterator();
            while (dependencyIterator.hasNext()) {
                TypedDependency dependency = dependencyIterator.next();
                System.out.println(dependency);
                String depString = dependency.reln().toString();
                if (depString.equals("compound") || depString.equals("amod")) {
                    String dep = dependency.dep().toString();
                    String gov = dependency.gov().toString();
                    nounP.add(dep.substring(0, dep.lastIndexOf("/")) + " " + gov.substring(0, gov.lastIndexOf("/")));
                }
            }
        }
    }
}
