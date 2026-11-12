package domox.dom.rules;

import domox.dom.crc.model.AssociationType;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link CandidateFactory}: the mapping from a persisted {@link RuleMatch} to a
 * {@link Candidate}, one candidate type at a time, including the blocked-noun guard and the
 * property-type inference.
 *
 * <p>These tests used to live in {@link RuleMatchesTest} because the factory was a private part of
 * {@code RuleMatches}.</p>
 */
@ExtendWith(MockitoExtension.class)
class CandidateFactoryTest {

    private CandidateFactory classUnderTest;

    @Mock
    ClassCandidates mockClassCandidates;

    @Mock
    AttributeCandidates mockAttributeCandidates;

    @Mock
    ResponsibilityCandidates mockResponsibilityCandidates;

    @Mock
    CollaboratorCandidates mockCollaboratorCandidates;

    @Mock
    NlpProperties mockNlpProperties;

    @BeforeEach
    public void setUp() {
        classUnderTest = new CandidateFactory(
                mockClassCandidates,
                mockAttributeCandidates,
                mockResponsibilityCandidates,
                mockCollaboratorCandidates,
                mockNlpProperties);
    }

    // ============ happy paths, one per candidate type ============

    @Test
    void createCandidatesFrom_createsClassAndAttributeCandidates() {
        // given
        final RuleMatch classMatch = match("ClassCdd", "Customer", null, null);
        final RuleMatch propertyMatch = match("PropertyCdd", "name", null, "Customer");

        final ClassCdd classCdd = new ClassCdd();
        final AttributeCdd propertyCdd = new AttributeCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);
        when(mockAttributeCandidates.findOrCreate("Customer", "name", "String", null)).thenReturn(propertyCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(classMatch, propertyMatch));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(classCdd);
        assertThat(result.get(1)).isEqualTo(propertyCdd);
        // each candidate records the match that produced it
        assertThat(classCdd.getRuleMatches()).containsExactly(classMatch);
        assertThat(propertyCdd.getRuleMatches()).containsExactly(propertyMatch);
    }

    @Test
    void createCandidatesFrom_createsResponsibilityCandidates() {
        // given
        final RuleMatch actionMatch = match("ActionCdd", "process", null, null);

        final ResponsibilityCdd responsibilityCdd = new ResponsibilityCdd();
        when(mockResponsibilityCandidates.findOrCreate("process", null, null)).thenReturn(responsibilityCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(responsibilityCdd);
    }

    @Test
    void createCandidatesFrom_createsResponsibilityCandidates_withOwningClass() {
        // given
        final RuleMatch actionMatch = match("ActionCdd", "offer", "ClassCdd", "Offer");

        final ClassCdd offerClassCdd = new ClassCdd();
        final ResponsibilityCdd offerAction = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Offer", null)).thenReturn(offerClassCdd);
        when(mockResponsibilityCandidates.findOrCreate("offer", offerClassCdd, null)).thenReturn(offerAction);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(offerAction);
        verify(mockClassCandidates).findOrCreate("Offer", null);
        verify(mockResponsibilityCandidates).findOrCreate("offer", offerClassCdd, null);
    }

    @Test
    void createCandidatesFrom_createsClassAssociationBetweenTwoClasses() {
        // given — a ClassCdd match carrying a related candidate name is an association
        final RuleMatch assocMatch = match("ClassCdd", "Payment", "ClassCdd", "Customer");

        final ClassCdd payment = new ClassCdd();
        final ClassCdd customer = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("Payment", null)).thenReturn(payment);
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);
        when(mockCollaboratorCandidates.findOrCreate("Payment_Customer", payment, customer, null)).thenReturn(assoc);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(assocMatch));

        // then
        assertThat(result).containsExactly(assoc);
        assertThat(payment.getCandidateName()).isEqualTo("Payment");
        assertThat(customer.getCandidateName()).isEqualTo("Customer");
    }

    @Test
    void createCandidatesFrom_createsGeneralizationAssociation() {
        // given — candidateName = child, relatedCandidateName = parent
        final RuleMatch genMatch = match("GeneralizationCdd", "OnlineShop", null, "Shop");

        final ClassCdd child = new ClassCdd();
        final ClassCdd parent = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("OnlineShop", null)).thenReturn(child);
        when(mockClassCandidates.findOrCreate("Shop", null)).thenReturn(parent);
        when(mockCollaboratorCandidates.findOrCreate(
                "OnlineShop_Shop", child, parent, null, AssociationType.GENERALIZATION)).thenReturn(assoc);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(genMatch));

        // then
        assertThat(result).containsExactly(assoc);
        assertThat(child.getCandidateName()).isEqualTo("OnlineShop");
        assertThat(parent.getCandidateName()).isEqualTo("Shop");
        verify(mockCollaboratorCandidates).findOrCreate(
                "OnlineShop_Shop", child, parent, null, AssociationType.GENERALIZATION);
    }

    @Test
    void createCandidatesFrom_createsSynonymAssociation() {
        // given
        final RuleMatch synMatch = match("SynonymCdd", "Store", "SynonymPartner", "Shop");

        final ClassCdd store = new ClassCdd();
        final ClassCdd shop = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("Store", null)).thenReturn(store);
        when(mockClassCandidates.findOrCreate("Shop", null)).thenReturn(shop);
        when(mockCollaboratorCandidates.findOrCreate(
                "Store_Shop", store, shop, null, AssociationType.SYNONYM)).thenReturn(assoc);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(synMatch));

        // then
        assertThat(result).containsExactly(assoc);
        assertThat(store.getCandidateName()).isEqualTo("Store");
        assertThat(shop.getCandidateName()).isEqualTo("Shop");
        verify(mockCollaboratorCandidates).findOrCreate(
                "Store_Shop", store, shop, null, AssociationType.SYNONYM);
    }

    // ============ de-duplication, empty and unknown input ============

    @Test
    void createCandidatesFrom_deduplicatesClassesByName() {
        // given — two matches for the same class name, but with distinct signatures so that
        // the second one is genuinely appended to the first candidate
        final RuleMatch classMatch1 = match("ClassCdd", "Customer", null, null);
        classMatch1.setDescription("first");
        final RuleMatch classMatch2 = match("ClassCdd", "Customer", null, null);
        classMatch2.setDescription("second");

        final ClassCdd classCdd = new ClassCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(classCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(classMatch1, classMatch2));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(classCdd);
        // findOrCreate must only be called once for the duplicated class name
        verify(mockClassCandidates, times(1)).findOrCreate("Customer", null);
        // …and the second match registers on the existing candidate
        assertThat(classCdd.getRuleMatches()).containsExactly(classMatch1, classMatch2);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForEmptyMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.emptyList());

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockClassCandidates, mockAttributeCandidates,
                mockResponsibilityCandidates, mockCollaboratorCandidates);
    }

    @Test
    void createCandidatesFrom_returnsEmptyListForNullMatches() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(null);

        // then
        assertThat(result).isEmpty();
        // a fresh, mutable list — not Collections.emptyList()
        assertThatCode(() -> result.add(null)).doesNotThrowAnyException();
        verifyNoInteractions(mockClassCandidates, mockAttributeCandidates,
                mockResponsibilityCandidates, mockCollaboratorCandidates);
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
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(classCdd);
    }

    @Test
    void createCandidatesFrom_skipsUnsupportedCandidateTypes() {
        // given
        final RuleMatch unknown = match("UnknownType", "doSomething", null, null);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(unknown));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockClassCandidates, mockAttributeCandidates,
                mockResponsibilityCandidates, mockCollaboratorCandidates);
    }

    @Test
    void createCandidatesFrom_skipsAGeneralizationWithoutARelatedName() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(
                Collections.singletonList(match("GeneralizationCdd", "OnlineShop", null, null)));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockClassCandidates, mockCollaboratorCandidates);
    }

    @Test
    void createCandidatesFrom_skipsASynonymWithoutARelatedName() {
        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(
                Collections.singletonList(match("SynonymCdd", "Store", null, null)));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockClassCandidates, mockCollaboratorCandidates);
    }

    @Test
    void createCandidatesFrom_usesUnknownAsOwningClassWhenThePropertyHasNoOwner() {
        // given
        final RuleMatch propMatch = match("PropertyCdd", "name", null, null);
        final AttributeCdd attribute = new AttributeCdd();
        when(mockAttributeCandidates.findOrCreate("Unknown", "name", "String", null)).thenReturn(attribute);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(propMatch));

        // then
        assertThat(result).containsExactly(attribute);
    }

    @Test
    void createCandidatesFrom_bindsCandidatesToTheGivenDomainModel() {
        // given
        final DomainModel model = new DomainModel();
        final RuleMatch classMatch = match("ClassCdd", "Customer", null, null);
        final ClassCdd classCdd = new ClassCdd();
        when(mockClassCandidates.findOrCreate("Customer", model)).thenReturn(classCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(List.of(classMatch), model);

        // then
        assertThat(result).containsExactly(classCdd);
        verify(mockClassCandidates).findOrCreate("Customer", model);
    }

    // ============ the blocked-noun guard ============

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
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(customer);
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
        assertThat(result).isEmpty();
        verifyNoInteractions(mockCollaboratorCandidates);
        verify(mockClassCandidates, never()).findOrCreate("Payment", null);
        verify(mockClassCandidates, never()).findOrCreate("Condition", null);
    }

    @Test
    void createCandidatesFrom_blocksOwningClassForAction_whenOwnerIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("system"));
        final RuleMatch actionMatch = match("ActionCdd", "validate", "ClassCdd", "System");

        final ResponsibilityCdd action = new ResponsibilityCdd();
        when(mockResponsibilityCandidates.findOrCreate("validate", null, null)).thenReturn(action);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(action);
        verify(mockClassCandidates, never()).findOrCreate("System", null);
        verify(mockResponsibilityCandidates).findOrCreate("validate", null, null);
    }

    @Test
    void createCandidatesFrom_skipsGeneralizationWhenParticipantIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("result"));
        final RuleMatch genMatch = match("GeneralizationCdd", "Result", null, "Parent");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(genMatch));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockCollaboratorCandidates);
        verify(mockClassCandidates, never()).findOrCreate("Result", null);
    }

    @Test
    void createCandidatesFrom_skipsSynonymWhenParticipantIsBlocked() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("result"));
        final RuleMatch synMatch = match("SynonymCdd", "Result", "SynonymPartner", "Partner");

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(synMatch));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockCollaboratorCandidates);
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
        assertThat(result).isEmpty();
        verify(mockAttributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void isBlockedUseCaseNoun_matchesSingularLemmaCaseInsensitively() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("step", "condition"));

        // expect
        assertThat(classUnderTest.isBlockedUseCaseNoun("Step")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("CONDITION")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("Customer")).isFalse();
        assertThat(classUnderTest.isBlockedUseCaseNoun(null)).isFalse();
    }

    @Test
    void isBlockedUseCaseNoun_fallsBackToTheSingularForAnUnlemmatizedPlural() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("condition"));

        // expect — "Conditions" reaches Phase 2 without lemmatization
        assertThat(classUnderTest.isBlockedUseCaseNoun("Conditions")).isTrue();
    }

    @Test
    void isBlockedUseCaseNoun_staysFalseWhenTheSingularFallbackIsNotConfigured() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("condition"));

        // expect — "Customers" reaches the plural fallback, but "customer" is not blocked
        assertThat(classUnderTest.isBlockedUseCaseNoun("Customers")).isFalse();
    }

    @Test
    void isBlockedUseCaseNoun_doesNotStripADoubleS() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("addres"));

        // expect — "address" ends with "ss", so the plural fallback must not strip it
        assertThat(classUnderTest.isBlockedUseCaseNoun("address")).isFalse();
    }

    @Test
    void isBlockedUseCaseNoun_isFalseWhenNoNounsAreConfigured() {
        // given — an explicit null list
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(null);

        // expect
        assertThat(classUnderTest.isBlockedUseCaseNoun("Customer")).isFalse();
    }

    // ============ property type inference ============

    @Test
    void inferType_defaultsToAString() {
        assertThat(classUnderTest.inferType("name")).isEqualTo("String");
        assertThat(classUnderTest.inferType(null)).isEqualTo("String");
    }

    @Test
    void inferType_recognisesNumericNames() {
        assertThat(classUnderTest.inferType("itemCount")).isEqualTo("int");
        assertThat(classUnderTest.inferType("phoneNumber")).isEqualTo("int");
        assertThat(classUnderTest.inferType("petAge")).isEqualTo("int");
        assertThat(classUnderTest.inferType("totalPrice")).isEqualTo("double");
        assertThat(classUnderTest.inferType("amountDue")).isEqualTo("double");
    }

    @Test
    void inferType_recognisesBooleanAndTemporalNames() {
        assertThat(classUnderTest.inferType("isActive")).isEqualTo("boolean");
        assertThat(classUnderTest.inferType("validUntil")).isEqualTo("boolean");
        assertThat(classUnderTest.inferType("enabled")).isEqualTo("boolean");
        assertThat(classUnderTest.inferType("startDate")).isEqualTo("LocalDateTime");
        assertThat(classUnderTest.inferType("elapsedTime")).isEqualTo("LocalDateTime");
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
