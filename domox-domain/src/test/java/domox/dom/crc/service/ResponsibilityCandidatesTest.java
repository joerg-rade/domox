package domox.dom.crc.service;

import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.ResponsibilityParameter;
import domox.dom.crc.repository.ResponsibilityCddRepository;
import domox.dom.rules.NlpProperties;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit guardrail for {@link ResponsibilityCandidates} — the operation's parameter list
 * ({@link ResponsibilityCandidates#addParameter}) and its UML return type
 * ({@link ResponsibilityCandidates#outputTypeFor}, the FIX-3b verb→return-type mapping).
 *
 * <p>Phase 2 runs over the same matches more than once (per document, and again after a re-analysis),
 * and {@code findOrCreate} returns the <em>existing</em> operation, so appending blindly would
 * duplicate every parameter on every pass. These cases pin that idempotence.</p>
 */
@ExtendWith(MockitoExtension.class)
class ResponsibilityCandidatesTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private FactoryService factoryService;

    @Mock
    private ResponsibilityCddRepository responsibilityCddRepository;

    @Mock
    private NlpProperties nlpProperties;

    private ResponsibilityCandidates classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new ResponsibilityCandidates(
                repositoryService, factoryService, responsibilityCddRepository, nlpProperties);
    }

    @Test
    void addsTheParameterToTheOperationAndPersistsIt() {
        // given
        when(factoryService.detachedEntity(ResponsibilityParameter.class))
                .thenAnswer(invocation -> new ResponsibilityParameter());
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", List.of(), "void");

        // when
        final ResponsibilityParameter parameter = classUnderTest.addParameter(browse, "pet", "Pet");

        // then
        assertThat(parameter.getCandidateName()).isEqualTo("pet");
        assertThat(parameter.getType()).isEqualTo("Pet");
        assertThat(parameter.responsibilityCdd).isSameAs(browse);
        assertThat(browse.inputTypeList).containsExactly(parameter);
        verify(repositoryService).persist(parameter);
    }

    /** A re-run must not add a second parameter of the same name. */
    @Test
    void doesNotAddTheSameParameterTwice() {
        // given — the operation already carries "pet"
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", List.of(), "void");
        final ResponsibilityParameter existing = new ResponsibilityParameter("pet", "Pet");
        browse.inputTypeList.add(existing);

        // when
        final ResponsibilityParameter parameter = classUnderTest.addParameter(browse, "pet", "Pet");

        // then
        assertThat(parameter).isSameAs(existing);
        assertThat(browse.inputTypeList).hasSize(1);
        verify(factoryService, never()).detachedEntity(any());
    }

    @Test
    void ignoresANullOperationOrANullName() {
        final ResponsibilityCdd browse = new ResponsibilityCdd("browse", List.of(), "void");

        assertThat(classUnderTest.addParameter(null, "pet", "Pet")).isNull();
        assertThat(classUnderTest.addParameter(browse, null, "Pet")).isNull();
        assertThat(browse.inputTypeList).isEmpty();
    }

    // ============ the verb→return-type mapping (FIX-3b, UML_GAP_FIX_PLAN.md §13.2) ============

    /** An output/notify verb returns something to read. */
    @Test
    void anOutputVerbReturnsString() {
        // given
        when(nlpProperties.getSystemOutputVerbs()).thenReturn(List.of("display", "show", "browse"));

        // when / then
        assertThat(classUnderTest.outputTypeFor("browse", null)).isEqualTo("String");
        assertThat(classUnderTest.outputTypeFor("Show", null)).isEqualTo("String");
    }

    /** A receive verb returns the class it receives — the class the operation is attached to. */
    @Test
    void aReceiveVerbReturnsTheReceivedClass() {
        // given
        final ClassCdd payment = new ClassCdd();
        payment.setCandidateName("Payment");
        when(nlpProperties.getReceiveVerbs()).thenReturn(List.of("receive", "obtain"));

        // when / then
        assertThat(classUnderTest.outputTypeFor("receive", payment)).isEqualTo("Payment");
    }

    /** A receive verb on an orphan operation has no received class to name. */
    @Test
    void aReceiveVerbWithoutAnOwnerHasNoReturnType() {
        // given
        when(nlpProperties.getReceiveVerbs()).thenReturn(List.of("receive"));

        // when / then
        assertThat(classUnderTest.outputTypeFor("receive", null)).isNull();
    }

    /** Every other verb renders no return type — deliberately not "void". */
    @Test
    void anyOtherVerbHasNoReturnType() {
        // given
        when(nlpProperties.getSystemOutputVerbs()).thenReturn(List.of("show"));
        when(nlpProperties.getReceiveVerbs()).thenReturn(List.of("receive"));

        // when / then
        assertThat(classUnderTest.outputTypeFor("process", new ClassCdd())).isNull();
        assertThat(classUnderTest.outputTypeFor(null, new ClassCdd())).isNull();
    }

    /** The mapping reaches the operation `create` builds, and thus the PlantUML rendering. */
    @Test
    void createSetsTheReturnTypeFromTheVerb() {
        // given
        when(nlpProperties.getSystemOutputVerbs()).thenReturn(List.of("browse"));
        when(factoryService.detachedEntity(ResponsibilityCdd.class))
                .thenAnswer(invocation -> new ResponsibilityCdd());

        // when
        final ResponsibilityCdd browse = classUnderTest.create("browse", null, null);

        // then — the non-void return type reaches the rendered operation line (there is no
        // getOutputType(): the field is write-only, and the UML rendering is the contract)
        assertThat(browse.toPlantUmlString()).isEqualTo("browse(): String");
    }
}
