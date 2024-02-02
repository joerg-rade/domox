package domox;

import domox.dom.nlp.Relation;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.Word;
import domox.dom.rqm.Author;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.Document;
import domox.dom.uml.DomainModel;
import org.apache.causeway.testing.fixtures.applib.fixturescripts.FixtureScript;
import org.apache.causeway.testing.fixtures.applib.modules.ModuleWithFixtures;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan(basePackageClasses = {
        Relation.class,
        Sentence.class,
        TypedDependency.class,
        Word.class,
        Author.class,
        Corpus.class,
        Document.class,
        DomainModel.class
})
public class DomainModule implements ModuleWithFixtures {

    @Override
    public FixtureScript getTeardownFixture() {
        return new FixtureScript() {
            @Override
            protected void execute(ExecutionContext executionContext) {
                repositoryService.removeAll(Author.class);
            }
        };
    }

}
