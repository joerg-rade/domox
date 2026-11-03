package domox.webapp.unittests.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import domox.DomainModule;
import jakarta.persistence.Entity;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture guardrails, enforced by the ArchUnit JUnit-5 engine.
 *
 * <p><b>Scope is {@code domox.*}, not {@code domox.webapp.*}.</b>  These rules are about JPA
 * entities and Causeway domain objects, which live in {@code domox-domain} — anchoring the import on
 * {@code Application.class} (as this class used to) imports zero of them and makes every rule
 * vacuous.  {@link DomainModule} is the root of the domain module, so {@code packagesOf} it covers
 * both {@code domox.dom.*} and {@code domox.webapp.*}.</p>
 *
 * <p>Requires {@code archunit-junit5-engine} on the test classpath: with only the API present
 * surefire reports "Tests run: 0" and the rules never run at all.</p>
 */
@AnalyzeClasses(
        packagesOf = {DomainModule.class},
        importOptions = {
                ImportOption.DoNotIncludeTests.class
        })
public class ArchitectureTests {

    /** ArchUnit's fluent API has no abstract-class filter, so express it as a predicate. */
    private static final DescribedPredicate<JavaClass> NOT_ABSTRACT =
            new DescribedPredicate<>("are not abstract") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    return !javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
                }
            };

    /**
     * A concrete JPA entity must also be a Causeway {@link DomainObject}.
     * <p>
     * Abstract entities are excluded: {@code Candidate} is the TABLE_PER_CLASS base for the CRC
     * candidates, is never instantiated or rendered on its own, and so carries no
     * {@code @DomainObject} — its concrete subclasses all do.
     */
    @ArchTest
    static ArchRule concrete_entities_are_also_DomainObjects =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .and(NOT_ABSTRACT)
                    .should().beAnnotatedWith(DomainObject.class);

    /**
     * {@code @D3Graph} is honoured only on a <b>property</b> (or on a domain object's type) — the
     * extension's {@code D3PanelComponentFactory} keys on the property's {@code D3GraphFacet}.  On an
     * <em>action</em> it is a silent no-op: the action returns its String and the viewer shows text.
     * <p>
     * That is not hypothetical — it is exactly the bug reported against
     * {@code Corpora#searchOntologyAsD3Graph}, which had to be changed to return a view model whose
     * {@code diagram} property carries the facet.  A property getter is {@code getX()}/{@code isX()};
     * anything else annotated {@code @D3Graph} is an action and will be ignored.
     */
    @ArchTest
    static ArchRule D3Graph_is_only_placed_on_property_getters =
            methods()
                    .that().areAnnotatedWith(D3Graph.class)
                    .should().haveNameMatching("(get|is)[A-Z].*");

    /**
     * Every Causeway {@link DomainObject} needs a {@link DomainObjectLayout} (the framework logs a
     * warning otherwise and the object renders without its title/icon/table decoration).
     */
    @ArchTest
    static ArchRule DomainObjects_are_also_DomainObjectLayout =
            classes()
                    .that().areAnnotatedWith(DomainObject.class)
                    .should().beAnnotatedWith(DomainObjectLayout.class);

    /*
     * REMOVED: "classes annotated @Entity are also annotated @XmlJavaTypeAdapter".
     *
     * The Causeway archetype ships that rule, but it is not a DoMoX convention and is not required:
     * the adapter only matters when an entity is marshalled (JAXB) — e.g. as an action parameter in
     * an XML representation.  Fourteen entities legitimately do not have it (Corpus, DomainModel,
     * Document, Sentence, Review, …) while Candidate does, and the CRC subclasses inherit the
     * behaviour from their base.  Enabling the engine made it fail 14 times on the first run, i.e. it
     * was a false-positive generator rather than a guardrail.  If a *targeted* version is ever wanted,
     * it should cover only entities that actually appear as action parameters.
     */

}
