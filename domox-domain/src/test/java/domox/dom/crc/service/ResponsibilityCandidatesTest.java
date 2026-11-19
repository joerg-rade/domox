package domox.dom.crc.service;

import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.ResponsibilityParameter;
import domox.dom.crc.repository.ResponsibilityCddRepository;
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
 * Unit guardrail for {@link ResponsibilityCandidates#addParameter} — the operation's parameter list.
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

    private ResponsibilityCandidates classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new ResponsibilityCandidates(
                repositoryService, factoryService, responsibilityCddRepository);
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
}
