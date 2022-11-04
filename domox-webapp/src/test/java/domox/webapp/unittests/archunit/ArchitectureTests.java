package domox.webapp.unittests.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import domox.webapp.Application;
import domox.webapp.application.ApplicationModule;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;

import javax.persistence.Entity;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packagesOf = {ApplicationModule.class, Application.class},
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        })
public class ArchitectureTests {

    @ArchTest
    static ArchRule package_dependencies =
            layeredArchitecture()
                    .layer("simple module").definedBy("domainapp.modules.simple..")
                    .layer("webapp").definedBy("domainapp.webapp..")
                    .whereLayer("simple module").mayOnlyBeAccessedByLayers("webapp");

    @ArchTest
    static ArchRule classes_annotated_with_Entity_are_also_annotated_with_DomainObject =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should().beAnnotatedWith(DomainObject.class);

    @ArchTest
    static ArchRule classes_annotated_with_Entity_are_also_annotated_with_XmlJavaTypeAdapter =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should().beAnnotatedWith(XmlJavaTypeAdapter.class);

    @ArchTest
    static ArchRule classes_annotated_with_DomainObject_are_also_annotated_with_DomainObjectLayout =
            classes()
                    .that().areAnnotatedWith(DomainObject.class)
                    .should().beAnnotatedWith(DomainObjectLayout.class);

}
