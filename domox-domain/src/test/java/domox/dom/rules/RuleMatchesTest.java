package domox.dom.rules;

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

    @BeforeEach
    public void setUp() {
        classUnderTest = new RuleMatches(
                mockRepositoryService,
                mockFactoryService,
                mockRuleMatchRepository,
                mockSentenceRepository,
                mockClassCandidates,
                mockPropertyCandidates);
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
        assertEquals(classCdd, result.get(0));
        // findOrCreate must only be called once for the duplicated class name
        verify(mockClassCandidates, times(1)).findOrCreate("Customer", null);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForEmptyMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.emptyList());

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForNullMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(null);

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates);
    }

    @Test
    void createCandidatesFrom_skipsUnsupportedCandidateTypes() {
        // given
        final RuleMatch unknown = match("ActionCdd", "doSomething", null, null);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(unknown));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates);
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
        assertEquals(classCdd, result.get(0));
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
        assertEquals(classCdd, result.get(0));
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