package domox.dom.rules;

import domox.dom.crc.ActionCandidates;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.Candidate;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.ClassCandidates;
import domox.dom.crc.PropertyCdd;
import domox.dom.crc.PropertyCandidates;
import domox.dom.nlp.SentenceRepository;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleMatchesTest {

    private RuleMatches classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    FactoryService mockFactoryService;

    @Mock
    RuleMatchRepository mockRuleMatchRepository;

    @Mock
    SentenceRepository mockSentenceRepository;

    @Mock
    ClassCandidates mockClassCandidates;

    @Mock
    PropertyCandidates mockPropertyCandidates;

    @Mock
    ActionCandidates mockActionCandidates;

    @BeforeEach
    public void setUp() {
        classUnderTest = new RuleMatches(
                mockRepositoryService,
                mockFactoryService,
                mockRuleMatchRepository,
                mockSentenceRepository,
                mockClassCandidates,
                mockPropertyCandidates,
                mockActionCandidates);
    }

    @Test
    void createCandidatesFrom_createsClassAndPropertyCandidates() {
        // given
        final RuleMatch classMatch = match("ClassCdd", "Customer", null, null);
        final RuleMatch propertyMatch = match("PropertyCdd", "name", null, "Customer");

        final ClassCdd classCdd = new ClassCdd();
        final PropertyCdd propertyCdd = new PropertyCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);
        when(mockPropertyCandidates.findOrCreate("Customer", "name", "String", null)).thenReturn(propertyCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(classMatch, propertyMatch));

        // then
        assertEquals(2, result.size());
        assertEquals(classCdd, result.get(0));
        assertEquals(propertyCdd, result.get(1));
    }

@Test
    void createCandidatesFrom_createsActionCandidates() {
        // given
        final RuleMatch actionMatch = match("ActionCdd", "process", null, null);

        final ActionCdd actionCdd = new ActionCdd();
        when(mockActionCandidates.findOrCreate("process", null, null)).thenReturn(actionCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(actionCdd, result.getFirst());
    }
    @Test
    void createCandidatesFrom_createsActionCandidates_withOwningClass() {
        // given
        final RuleMatch actionMatch = match("ActionCdd", "offer", "ClassCdd", "Offer");

        final ClassCdd offerClassCdd = new ClassCdd();
        final ActionCdd offerAction = new ActionCdd();
        when(mockClassCandidates.findOrCreate("Offer", null)).thenReturn(offerClassCdd);
        when(mockActionCandidates.findOrCreate("offer", offerClassCdd, null)).thenReturn(offerAction);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(offerAction, result.getFirst());
        verify(mockClassCandidates).findOrCreate("Offer", null);
        verify(mockActionCandidates).findOrCreate("offer", offerClassCdd, null);
    }

    @Test
    void createCandidatesFrom_deduplicatesClassesByName() {
        // given
        final RuleMatch classMatch1 = match("ClassCdd", "Customer", null, null);
        final RuleMatch classMatch2 = match("ClassCdd", "Customer", null, null);

        final ClassCdd classCdd = new ClassCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(classMatch1, classMatch2));

        // then
        assertEquals(1, result.size());
        assertEquals(classCdd, result.getFirst());
        // findOrCreate must only be called once for the duplicated class name
        verify(mockClassCandidates, times(1)).findOrCreate("Customer", null);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForEmptyMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.emptyList());

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForNullMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(null);

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates);
    }

    @Test
    void createCandidatesFrom_skipsUnsupportedCandidateTypes() {
        // given
        final RuleMatch unknown = match("UnknownType", "doSomething", null, null);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(unknown));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates);
    }

    @Test
    void createCandidatesFrom_skipsNullMatchInList() {
        // given
        final RuleMatch classMatch = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(null, classMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(classCdd, result.getFirst());
    }

    @Test
    void createCandidatesFromMatches_usesAllPersistedMatches() {
        // given
        final RuleMatch classMatch = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        when(mockRuleMatchRepository.findAll()).thenReturn(Collections.singletonList(classMatch));
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFromMatches();

        // then
        assertEquals(1, result.size());
        assertEquals(classCdd, result.getFirst());
    }

    // -- helper

    private static RuleMatch match(String candidateType, String candidateName, String relatedType, String relatedName) {
        final RuleMatch match = new RuleMatch();
        match.setCandidateType(candidateType);
        match.setCandidateName(candidateName);
        match.setRelatedCandidateType(relatedType);
        match.setRelatedCandidateName(relatedName);
        return match;
    }
}