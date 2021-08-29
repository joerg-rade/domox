package domox.webapp;

import domox.dom.nlp.Relation;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.nlp.Word;
import domox.dom.rqm.Author;
import domox.dom.rqm.Corpus;
import domox.dom.rqm.Document;
import org.apache.isis.core.config.presets.IsisPresets;
import org.apache.isis.core.runtimeservices.IsisModuleCoreRuntimeServices;
import org.apache.isis.persistence.jpa.eclipselink.IsisModulePersistenceJpaEclipselink;
import org.apache.isis.security.bypass.IsisModuleSecurityBypass;
import org.apache.isis.viewer.wicket.viewer.IsisModuleViewerWicketViewer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        IsisModuleCoreRuntimeServices.class, // Apache Isis Runtime
        IsisModulePersistenceJpaEclipselink.class, // EclipseLink as JPA provider for Spring Data
        IsisModuleViewerWicketViewer.class, // UI (Wicket Viewer)
        IsisModuleSecurityBypass.class // Security (Bypass, grants all access)
})
@EntityScan(basePackageClasses = {
        Relation.class,
        Sentence.class,
        TypedDependency.class,
        Word.class,
        Author.class,
        Corpus.class,
        Document.class,
})
public class Application {

    /**
     * @implNote this is to support the <em>Spring Boot Maven Plugin</em>, which auto-detects an
     * entry point by searching for classes having a {@code main(...)}
     */
    public static void main(String[] args) {
        IsisPresets.prototyping();
        SpringApplication.run(new Class[]{Application.class}, args);
    }

}
