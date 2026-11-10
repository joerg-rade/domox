package domox.dom.crc.service;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.rules.RuleMatches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the single "Candidates" menu service exposes each candidate
 * list action and delegates to the correct {@code *Candidates} service.
 */
@ExtendWith(MockitoExtension.class)
class CandidatesTest {

    @Mock
    ClassCandidates mockClassCandidates;
    @Mock
    ResponsibilityCandidates mockResponsibilityCandidates;
    @Mock
    AttributeCandidates mockAttributeCandidates;
    @Mock
    CollaboratorCandidates mockCollaboratorCandidates;
    @Mock
    RuleMatches mockRuleMatches;

    private Candidates classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new Candidates(
                mockClassCandidates,
                mockResponsibilityCandidates,
                mockAttributeCandidates,
                mockCollaboratorCandidates,
                mockRuleMatches);
    }

    @Test
    void listAllClasses_returnsClassCandidates_andOnlyTouchesClassCandidates() {
        // given
        final ClassCdd clazz = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(mockClassCandidates.listAll()).thenReturn(List.of(clazz));

        // when
        final List<ClassCdd> result = classUnderTest.listAllClasses();

        // then
        assertThat(result).isEqualTo(List.of(clazz));
        verify(mockClassCandidates).listAll();
        verify(mockResponsibilityCandidates, never()).listAll();
        verify(mockAttributeCandidates, never()).listAll();
        verify(mockCollaboratorCandidates, never()).listAll();
    }

    @Test
    void listAllResponsibilities_returnsResponsibilityCandidates() {
        // given
        final ResponsibilityCdd action = new ResponsibilityCdd("process", new ArrayList<>(), "void");
        when(mockResponsibilityCandidates.listAll()).thenReturn(List.of(action));

        // when
        final List<ResponsibilityCdd> result = classUnderTest.listAllResponsibilities();

        // then
        assertThat(result).isEqualTo(List.of(action));
        verify(mockResponsibilityCandidates).listAll();
        verify(mockClassCandidates, never()).listAll();
    }

    @Test
    void listAllAttributes_returnsAttributeCandidates() {
        // given
        final AttributeCdd property = new AttributeCdd("name", "String");
        when(mockAttributeCandidates.listAll()).thenReturn(List.of(property));

        // when
        final List<AttributeCdd> result = classUnderTest.listAllAttributes();

        // then
        assertThat(result).isEqualTo(List.of(property));
        verify(mockAttributeCandidates).listAll();
    }

    @Test
    void listAllCollaborators_returnsCollaboratorCandidates() {
        // given
        final ClassCdd source = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ClassCdd target = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final CollaboratorCdd association = new CollaboratorCdd("places", source, target);
        when(mockCollaboratorCandidates.listAll()).thenReturn(List.of(association));

        // when
        final List<CollaboratorCdd> result = classUnderTest.listAllCollaborators();

        // then
        assertThat(result).isEqualTo(List.of(association));
        verify(mockCollaboratorCandidates).listAll();
    }
}