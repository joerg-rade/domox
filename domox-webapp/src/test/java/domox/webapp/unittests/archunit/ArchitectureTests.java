package domox.webapp.unittests.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import domox.DomainModule;
import jakarta.persistence.Entity;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;

import java.util.Set;
import java.util.stream.Collectors;

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
     * {@code OntologyDiagrams#searchOntologyAsD3Graph}, which had to be changed to return a view model whose
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

    /**
     * A JPA entity must not expose the <em>same</em> member twice to JAXB: a {@code public} field
     * plus a getter of the same name ({@code attributeList} and {@code getAttributeList()}) are two
     * JAXB properties with one name, and Causeway marshals a list of domain objects with JAXB — so
     * the duplicate makes every RestfulObjects list endpoint fail with HTTP 500:
     *
     * <pre>
     * IllegalAnnotationsException: Class has two properties of the same name "attributeList"
     *   at public java.util.List domox.dom.crc.model.ClassCdd.getAttributeList()
     *   at public java.util.List domox.dom.crc.model.ClassCdd.attributeList
     * </pre>
     *
     * <p>The 2026-11 occurrence was six members across four classes ({@code ClassCdd} ×4,
     * {@code DomainModel} ×4, {@code Document.authors}, {@code ResponsibilityParameter.responsibilityCdd})
     * and the metamodel validator did <em>not</em> catch it — it is a serialization-time failure, so
     * only an RO call or this rule can see it.</p>
     *
     * <p>The convention is therefore: a {@code public} field is the member, and it carries no
     * {@code @Getter} ({@code @Setter} is fine, and a {@code private} field needs its getter).</p>
     */
    @ArchTest
    static ArchRule entity_public_fields_have_no_getter_of_the_same_name =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should(new ArchCondition<JavaClass>(
                            "not declare a public field together with a getter of the same name") {
                        @Override
                        public void check(final JavaClass javaClass, final ConditionEvents events) {
                            final Set<String> getterProperties = javaClass.getAllMethods().stream()
                                    .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                                    .filter(method -> method.getRawParameterTypes().isEmpty())
                                    .map(JavaMethod::getName)
                                    .filter(ArchitectureTests::isGetterName)
                                    .map(ArchitectureTests::propertyNameOf)
                                    .collect(Collectors.toSet());
                            for (final JavaField field : javaClass.getAllFields()) {
                                if (!field.getModifiers().contains(JavaModifier.PUBLIC)) {
                                    continue;
                                }
                                if (getterProperties.contains(field.getName())) {
                                    events.add(SimpleConditionEvent.violated(field,
                                            javaClass.getName() + " declares public field '" + field.getName()
                                                    + "' and also a getter of the same name — JAXB sees two"
                                                    + " properties called '" + field.getName() + "' and every"
                                                    + " RestfulObjects list endpoint fails with HTTP 500."
                                                    + " Drop the @Getter and read the public field."));
                                }
                            }
                        }
                    });

    private static boolean isGetterName(final String methodName) {
        return (methodName.startsWith("get") || methodName.startsWith("is"))
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3));
    }

    /** {@code getAttributeList} / {@code isActive} → {@code attributeList} / {@code active}. */
    private static String propertyNameOf(final String methodName) {
        final int prefix = methodName.startsWith("is") ? 2 : 3;
        return Character.toLowerCase(methodName.charAt(prefix)) + methodName.substring(prefix + 1);
    }

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
