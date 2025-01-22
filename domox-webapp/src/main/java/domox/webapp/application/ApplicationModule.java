package domox.webapp.application;

import domox.dom.Analysis;
import domox.dom.nlp.Relation;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.Word;
import domox.dom.rqm.Author;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.Document;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Import({})
@ComponentScan(basePackages = {"domox.dom.nlp", "domox.dom.rqm"})
@EnableJpaRepositories
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
public class ApplicationModule {

}
