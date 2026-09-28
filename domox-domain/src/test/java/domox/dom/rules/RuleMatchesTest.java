package domox.dom.rules;

import domox.dom.crc.ActionCandidates;
import domox.dom.crc.ActionCdd;
import domox.dom.crc.AssociationCandidates;
import domox.dom.crc.AssociationCdd;
import domox.dom.crc.AssociationType;
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
import static org.apache.causeway.commons.internal.assertions._Assert.assertFalse;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
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

    @Mock
    AssociationCandidates mockAssociationCandidates;

    @Mock
    NlpProperties mockNlpProperties;

    @BeforeEach
    public void setUp() {
        classUnderTest = new RuleMatches(
                mockRepositoryService,
                mockFactoryService,
                mockRuleMatchRepository,
                mockSentenceRepository,
                mockClassCandidates,
                mockPropertyCandidates,
                mockActionCandidates,
                mockAssociationCandidates,
                mockNlpProperties);
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
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates, mockAssociationCandidates);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForNullMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(null);

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates, mockAssociationCandidates);
    }

    @Test
    void createCandidatesFrom_skipsUnsupportedCandidateTypes() {
        // given
        final RuleMatch unknown = match("UnknownType", "doSomething", null, null);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(unknown));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockClassCandidates, mockPropertyCandidates, mockActionCandidates, mockAssociationCandidates);
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

    @Test
    void createCandidatesFrom_skipsBlockedUseCaseNouns_forClassCandidates() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("flow", "step", "condition"));
        final RuleMatch blockedClassMatch = match("ClassCdd", "Flow", null, null);
        final RuleMatch normalClassMatch = match("ClassCdd", "Customer", null, null);

        final ClassCdd customer = new ClassCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(blockedClassMatch, normalClassMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(customer, result.getFirst());
        verify(mockClassCandidates, never()).findOrCreate("Flow", null);
        verify(mockClassCandidates).findOrCreate("Customer", null);
    }

    @Test
    void createCandidatesFrom_skipsAssociationWhenParticipantIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("condition"));
        final RuleMatch assocMatch = match("ClassCdd", "Payment", "ClassCdd", "Condition");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(assocMatch));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockAssociationCandidates);
        verify(mockClassCandidates, never()).findOrCreate("Payment", null);
        verify(mockClassCandidates, never()).findOrCreate("Condition", null);
    }

    @Test
    void createCandidatesFrom_blocksOwningClassForAction_whenOwnerIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("system"));
        final RuleMatch actionMatch = match("ActionCdd", "validate", "ClassCdd", "System");

        final ActionCdd action = new ActionCdd();
        when(mockActionCandidates.findOrCreate("validate", null, null)).thenReturn(action);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(action, result.getFirst());
        verify(mockClassCandidates, never()).findOrCreate("System", null);
        verify(mockActionCandidates).findOrCreate("validate", null, null);
    }

    @Test
    void createCandidatesFrom_skipsGeneralizationWhenParticipantIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("result"));
        final RuleMatch genMatch = match("GeneralizationCdd", "Result", null, "Parent");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(genMatch));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockAssociationCandidates);
        verify(mockClassCandidates, never()).findOrCreate("Result", null);
    }

    @Test
    void createCandidatesFrom_createsSynonymAssociation() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of());
        final RuleMatch synMatch = match("SynonymCdd", "Store", "SynonymPartner", "Shop");

        final ClassCdd store = new ClassCdd();
        store.setCandidateName("Store");
        final ClassCdd shop = new ClassCdd();
        shop.setCandidateName("Shop");
        final AssociationCdd assoc = new AssociationCdd();
        when(mockClassCandidates.findOrCreate("Store", null)).thenReturn(store);
        when(mockClassCandidates.findOrCreate("Shop", null)).thenReturn(shop);
        when(mockAssociationCandidates.findOrCreate(
                "Store_Shop", store, shop, null, AssociationType.SYNONYM)).thenReturn(assoc);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(synMatch));

        // then
        assertEquals(1, result.size());
        assertEquals(assoc, result.getFirst());
        verify(mockAssociationCandidates).findOrCreate(
                "Store_Shop", store, shop, null, AssociationType.SYNONYM);
    }

    @Test
    void createCandidatesFrom_skipsSynonymWhenParticipantIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("result"));
        final RuleMatch synMatch = match("SynonymCdd", "Result", "SynonymPartner", "Partner");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(synMatch));

        // then
        assertEquals(0, result.size());
        verifyNoInteractions(mockAssociationCandidates);
        verify(mockClassCandidates, never()).findOrCreate("Result", null);
    }
@Test
    void createCandidatesFrom_skipsPropertyWhenOwnerClassIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("condition"));
        final RuleMatch propMatch = match("PropertyCdd", "description", "ClassCdd", "Condition");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(propMatch));

        // then
        assertEquals(0, result.size());
        verify(mockPropertyCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void isBlockedUseCaseNoun_matchesSingularLemmaCaseInsensitively() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("step", "condition"));

        // expect
        assertTrue(classUnderTest.isBlockedUseCaseNoun("Step"));
        assertTrue(classUnderTest.isBlockedUseCaseNoun("CONDITION"));
        assertFalse(classUnderTest.isBlockedUseCaseNoun("Customer"));
        assertFalse(classUnderTest.isBlockedUseCaseNoun(null));
    }

@Test
    void create_returnsNullForBlockedUseCaseNoun() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("actor"));
        // ClassCdd with blocked name "Actor"
        RuleMatch result = classUnderTest.create(null, "TDR11", "ClassCdd", "Actor", null, null, null);

        // then
        assertNull(result);
        verifyNoInteractions(mockRepositoryService);
    }

    @Test
    void create_persistsUnblockedClassMatch() {
        // given
        final RuleMatch persisted = new RuleMatch();
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("actor"));
        when(mockFactoryService.detachedEntity(RuleMatch.class)).thenReturn(persisted);
        // ClassCdd with non-blocked name "Customer"

        // when
        RuleMatch result = classUnderTest.create(null, "TDR11", "ClassCdd", "Customer", null, null, null);

        // then — passes through the guard and returns the persisted match
        assertNotNull(result);
        assertSame(persisted, result);
        assertEquals("Customer", result.getCandidateName());
        assertEquals("ClassCdd", result.getCandidateType());
        verify(mockFactoryService).detachedEntity(RuleMatch.class);
        verify(mockRepositoryService).persist(persisted);
    }
@Test
    void create_skipsPersistingWhenDuplicateSignatureExists() {
        // given — an existing match already present for the same
        // candidate name/type, rule class, description and related candidate.
        final RuleMatch existing = new RuleMatch();
        existing.setRuleClassName("TDR11");
        existing.setCandidateType("PropertyCdd");
        existing.setCandidateName("range");
        existing.setRelatedCandidateType("ClassCdd");
        existing.setRelatedCandidateName("ClassCdd");
        existing.setDescription("Entity.add(range)");
        when(mockRuleMatchRepository
                .findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
                        "range", "PropertyCdd", "TDR11", "Entity.add(range)", "ClassCdd", "ClassCdd"))
                .thenReturn(Collections.singletonList(existing));

        // when — create the same signature again
        final RuleMatch result = classUnderTest.create(
                null, "TDR11", "PropertyCdd", "range", "ClassCdd", "ClassCdd", "Entity.add(range)");

        // then — no second match is persisted; the existing one is returned
        assertSame(existing, result);
        verifyNoInteractions(mockFactoryService);
        verify(mockRepositoryService, never()).persist(any());
    }

    @Test
    void create_persistsWhenNoDuplicateExists() {
        // given — repository reports no existing match with this signature
        when(mockRuleMatchRepository
                .findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        final RuleMatch persisted = new RuleMatch();
        when(mockFactoryService.detachedEntity(RuleMatch.class)).thenReturn(persisted);

        // when
        final RuleMatch result = classUnderTest.create(
                null, "TDR6", "PropertyCdd", "range", "ClassCdd", "ClassCdd", "Entity.add(range)");

        // then — a fresh match is persisted and returned
        assertSame(persisted, result);
        verify(mockRepositoryService).persist(persisted);
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