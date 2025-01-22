package domox.webapp;

import domox.dom.Analysis;
import domox.dom.nlp.Relation;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.Word;
import domox.dom.rqm.Author;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.Document;
import org.apache.causeway.core.config.presets.CausewayPresets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        AppManifest.class,
//    , XrayEnable.class
})
@ComponentScan
@EntityScan(basePackageClasses = {
        Analysis.class,
        Author.class,
        Corpus.class,
        Document.class,
        Relation.class,
        Sentence.class,
        TypedDependency.class,
        Word.class
})
public class Application /*extends SpringBootServletInitializer*/ {

    /**
     * @implNote this is to support the <em>Spring Boot Maven Plugin</em>, which auto-detects an
     * entry point by searching for classes having a {@code main(...)}
     */
    public static void main(String[] args) {
        CausewayPresets.prototyping();
        SpringApplication.run(new Class[]{Application.class}, args);
    }

}
