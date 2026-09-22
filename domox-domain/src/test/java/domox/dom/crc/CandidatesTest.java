package domox.dom.crc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

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
    ActionCandidates mockActionCandidates;
    @Mock
    PropertyCandidates mockPropertyCandidates;
    @Mock
    AssociationCandidates mockAssociationCandidates;

    private Candidates classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new Candidates(
                mockClassCandidates,
                mockActionCandidates,
                mockPropertyCandidates,
                mockAssociationCandidates);
    }

    @Test
    void listAllClasses_returnsClassCandidates_andOnlyTouchesClassCandidates() {
        // given
        final ClassCdd clazz = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(mockClassCandidates.listAll()).thenReturn(List.of(clazz));

        // when
        final List<ClassCdd> result = classUnderTest.listAllClasses();

        // then
        org.junit.jupiter.api.Assertions.assertEquals(List.of(clazz), result);
        verify(mockClassCandidates).listAll();
        verify(mockActionCandidates, never()).listAll();
        verify(mockPropertyCandidates, never()).listAll();
        verify(mockAssociationCandidates, never()).listAll();
    }

    @Test
    void listAllActions_returnsActionCandidates() {
        // given
        final ActionCdd action = new ActionCdd("process", new ArrayList<>(), "void");
        when(mockActionCandidates.listAll()).thenReturn(List.of(action));

        // when
        final List<ActionCdd> result = classUnderTest.listAllActions();

        // then
        org.junit.jupiter.api.Assertions.assertEquals(List.of(action), result);
        verify(mockActionCandidates).listAll();
        verify(mockClassCandidates, never()).listAll();
    }

    @Test
    void listAllProperties_returnsPropertyCandidates() {
        // given
        final PropertyCdd property = new PropertyCdd("name", "String");
        when(mockPropertyCandidates.listAll()).thenReturn(List.of(property));

        // when
        final List<PropertyCdd> result = classUnderTest.listAllProperties();

        // then
        org.junit.jupiter.api.Assertions.assertEquals(List.of(property), result);
        verify(mockPropertyCandidates).listAll();
    }

    @Test
    void listAllAssociations_returnsAssociationCandidates() {
        // given
        final ClassCdd source = new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final ClassCdd target = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final AssociationCdd association = new AssociationCdd("places", source, target);
        when(mockAssociationCandidates.listAll()).thenReturn(List.of(association));

        // when
        final List<AssociationCdd> result = classUnderTest.listAllAssociations();

        // then
        org.junit.jupiter.api.Assertions.assertEquals(List.of(association), result);
        verify(mockAssociationCandidates).listAll();
    }
}