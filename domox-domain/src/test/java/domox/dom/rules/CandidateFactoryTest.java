package domox.dom.rules;

import domox.dom.AbstractEntity;
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
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        final RuleMatch propertyMatch = match("AttributeCdd", "name", null, "Customer");

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
        final RuleMatch actionMatch = match("ResponsibilityCdd", "process", null, null);

        final ResponsibilityCdd responsibilityCdd = new ResponsibilityCdd();
        when(mockResponsibilityCandidates.findOrCreate("process", null, null, null)).thenReturn(responsibilityCdd);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(responsibilityCdd);
    }

    @Test
    void createCandidatesFrom_createsResponsibilityCandidates_withOwningClass() {
        // given — the object noun is the only owner evidence (no sentence, so no actor)
        final RuleMatch actionMatch = match("ResponsibilityCdd", "offer", "ClassCdd", "Offer");

        final ClassCdd offerClassCdd = new ClassCdd();
        final ResponsibilityCdd offerAction = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Offer", null)).thenReturn(offerClassCdd);
        when(mockResponsibilityCandidates.findOrCreate("offer", offerClassCdd, offerClassCdd, null))
                .thenReturn(offerAction);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(offerAction);
        verify(mockClassCandidates).findOrCreate("Offer", null);
        verify(mockResponsibilityCandidates).findOrCreate("offer", offerClassCdd, offerClassCdd, null);
    }

    /**
     * FIX-3d ({@code UML_GAP_FIX_PLAN.md} §13.8): the operation is owned by the sentence's <b>actor</b>,
     * not by its object. {@code "the customer selects products"} is {@code Customer.select(products)} —
     * {@code Product.select(...)} is the defect this pins shut. The object class still travels with the
     * call, because a receive verb is typed by it (FIX-3b).
     */
    @Test
    void createCandidatesFrom_ownsTheOperationByTheSentencesActor() {
        // given — TDR3's match: the verb with its object as the related candidate …
        final Sentence sentence = sentenceWithId(11L);
        final RuleMatch actionMatch = match("ResponsibilityCdd", "select", "ClassCdd", "Product");
        actionMatch.setTypedDependency(dependencyIn(sentence));
        sentence.addTypedDependency(actorDependency(sentence, "select", "customer"));
        sentence.addTypedDependency(objectDependency(sentence, "select", "product"));

        final ClassCdd customer = new ClassCdd();
        final ClassCdd product = new ClassCdd();
        final ResponsibilityCdd select = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);
        when(mockClassCandidates.findOrCreate("Product", null)).thenReturn(product);
        when(mockResponsibilityCandidates.findOrCreate("select", customer, product, null)).thenReturn(select);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then — the owner is the actor, the object is only the received class
        assertThat(result).containsExactly(select);
        verify(mockResponsibilityCandidates).findOrCreate("select", customer, product, null);
    }

    /**
     * The fallback: when the sentence names no usable actor — here {@code System}, a blocked use-case
     * noun — the object keeps the operation, as it did before FIX-3d. Measured on the PetShop model this
     * is 62 of the 149 owned operations (19 naming an unusable actor such as {@code System}, 43 naming
     * none), and it is why the change loses no operation its owner.
     */
    @Test
    void createCandidatesFrom_fallsBackToTheObjectWhenTheSentenceNamesNoUsableActor() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("system"));
        final Sentence sentence = sentenceWithId(12L);
        final RuleMatch actionMatch = match("ResponsibilityCdd", "validate", "ClassCdd", "Availability");
        actionMatch.setTypedDependency(dependencyIn(sentence));
        sentence.addTypedDependency(actorDependency(sentence, "validate", "system"));

        final ClassCdd availability = new ClassCdd();
        final ResponsibilityCdd validate = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Availability", null)).thenReturn(availability);
        when(mockResponsibilityCandidates.findOrCreate("validate", availability, availability, null))
                .thenReturn(validate);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then — no class is invented for "System"; the object owns the operation
        assertThat(result).containsExactly(validate);
        verify(mockClassCandidates, never()).findOrCreate("System", null);
        verify(mockResponsibilityCandidates).findOrCreate("validate", availability, availability, null);
    }

    /**
     * The operation <em>signature</em>: the parameters are read from the sentence the action was
     * extracted from, and a parameter whose name is a class in the model is typed by that class
     * ("the customer browses a pet" → {@code Customer.browse(pet: Pet)}).
     */
    @Test
    void createCandidatesFrom_addsTheOperationsParameters() {
        // given — an action match carrying the sentence it came from
        final Sentence sentence = sentenceWithId(9L);
        final RuleMatch actionMatch = match("ResponsibilityCdd", "Browse", null, "Customer");
        actionMatch.setTypedDependency(dependencyIn(sentence));
        sentence.addTypedDependency(objectDependency(sentence, "browse", "pet"));

        final ClassCdd customer = new ClassCdd();
        final ClassCdd pet = new ClassCdd();
        pet.setCandidateName("Pet");
        final ResponsibilityCdd browse = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);
        when(mockResponsibilityCandidates.findOrCreate("Browse", customer, customer, null)).thenReturn(browse);
        when(mockClassCandidates.findByCandidateName("Pet", null)).thenReturn(pet);

        // when
        classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        verify(mockResponsibilityCandidates).addParameter(browse, "pet", "Pet");
    }

    /** A parameter whose name never became a class is a value — typed by the inferred built-in type. */
    @Test
    void createCandidatesFrom_infersTheParameterTypeWhenNoClassOfThatNameExists() {
        // given — no class candidate is found for "reference"
        final Sentence sentence = sentenceWithId(10L);
        final RuleMatch actionMatch = match("ResponsibilityCdd", "Provide", null, "Shop");
        actionMatch.setTypedDependency(dependencyIn(sentence));
        sentence.addTypedDependency(objectDependency(sentence, "provide", "reference"));

        final ClassCdd shop = new ClassCdd();
        final ResponsibilityCdd provide = new ResponsibilityCdd();
        when(mockClassCandidates.findOrCreate("Shop", null)).thenReturn(shop);
        when(mockResponsibilityCandidates.findOrCreate("Provide", shop, shop, null)).thenReturn(provide);

        // when
        classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        verify(mockResponsibilityCandidates).addParameter(provide, "reference", "String");
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

    /**
     * The multiplicities of an association come from the sentence the association was extracted from:
     * {@code "… 01 payments …"} states {@code 1} for the Payment end, {@code "many customers"} states
     * {@code *} for the Customer end.
     */
    @Test
    void createCandidatesFrom_putsTheSentenceMultiplicitiesOnBothAssociationEnds() {
        // given — one sentence, three matches: the association plus one multiplicity per end
        final Sentence sentence = sentenceWithId(7L);
        final RuleMatch assocMatch = match("ClassCdd", "Payment", "ClassCdd", "Customer");
        assocMatch.setTypedDependency(dependencyIn(sentence));
        final RuleMatch sourceMultiplicity = multiplicityMatch("TDR25", "Payment", "01", sentence);
        final RuleMatch targetMultiplicity = multiplicityMatch("TDR26", "Customer", "many", sentence);

        final ClassCdd payment = new ClassCdd();
        final ClassCdd customer = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("Payment", null)).thenReturn(payment);
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);
        when(mockCollaboratorCandidates.findOrCreate("Payment_Customer", payment, customer, null)).thenReturn(assoc);

        // when
        classUnderTest.createCandidatesFrom(Arrays.asList(assocMatch, sourceMultiplicity, targetMultiplicity));

        // then
        assertThat(assoc.getSourceCardinality()).as("TDR25 nummod(payment, 01)").isEqualTo("1");
        assertThat(assoc.getTargetCardinality()).as("TDR26 det(customer, many)").isEqualTo("*");
    }

    /** Evidence is sentence-scoped: a multiplicity stated elsewhere in the document must not be borrowed. */
    @Test
    void createCandidatesFrom_doesNotBorrowAMultiplicityFromAnotherSentence() {
        // given — the association lives in sentence 7, the multiplicity in sentence 8
        final RuleMatch assocMatch = match("ClassCdd", "Payment", "ClassCdd", "Customer");
        assocMatch.setTypedDependency(dependencyIn(sentenceWithId(7L)));
        final RuleMatch elsewhere = multiplicityMatch("TDR26", "Customer", "many", sentenceWithId(8L));

        final ClassCdd payment = new ClassCdd();
        final ClassCdd customer = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("Payment", null)).thenReturn(payment);
        when(mockClassCandidates.findOrCreate("Customer", null)).thenReturn(customer);
        when(mockCollaboratorCandidates.findOrCreate("Payment_Customer", payment, customer, null)).thenReturn(assoc);

        // when
        classUnderTest.createCandidatesFrom(Arrays.asList(assocMatch, elsewhere));

        // then — "" is the "not stated" value, and both columns are NOT NULL
        assertThat(assoc.getSourceCardinality()).isEmpty();
        assertThat(assoc.getTargetCardinality()).isEmpty();
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

    /**
     * An association match (TDR14–TDR23) is recorded as {@code ClassCdd} with a related candidate
     * name, so it must not be swallowed by the de-duplication of its <em>source</em> class.  Measured
     * on the PetShop model, 16 of the 30 recorded association pairs never produced a row exactly
     * because their source class had a plain class match earlier in the same document
     * (UML_GAP_FIX_PLAN.md §13.7) — {@code Pet_Daycare} among them.
     */
    @Test
    void createCandidatesFrom_doesNotSwallowAnAssociationBehindItsSourceClass() {
        // given — the source class is created first, then an association from that same class
        final RuleMatch classMatch = match("ClassCdd", "Pet", null, null);
        final RuleMatch assocMatch = match("ClassCdd", "Pet", "ClassCdd", "Daycare");

        final ClassCdd pet = new ClassCdd();
        final ClassCdd daycare = new ClassCdd();
        final CollaboratorCdd assoc = new CollaboratorCdd();
        when(mockClassCandidates.findOrCreate("Pet", null)).thenReturn(pet);
        when(mockClassCandidates.findOrCreate("Daycare", null)).thenReturn(daycare);
        when(mockCollaboratorCandidates.findOrCreate("Pet_Daycare", pet, daycare, null)).thenReturn(assoc);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Arrays.asList(classMatch, assocMatch));

        // then — the class is de-duplicated, the association is not
        assertThat(result).containsExactly(pet, assoc);
        assertThat(pet.getRuleMatches()).containsExactly(classMatch);
        assertThat(assoc.getRuleMatches()).containsExactly(assocMatch);
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

    /**
     * A property match with no resolvable owner is skipped, not attached to a fabricated class.
     * <p>
     * The old behaviour invented a class literally called {@code Unknown} to own such a property —
     * 12 attribute candidates hung off it in the PetShop model.  TDR12's "both sides are basic
     * attributes" branch ("size color", "description description") records exactly this shape: a
     * {@code AttributeCdd} whose {@code relatedCandidateName} is null, because the rule never
     * identifies an owning class for it.  With no owner there is no attribute.
     */
    @Test
    void createCandidatesFrom_skipsAPropertyWhoseOwnerCannotBeResolved() {
        // given
        final RuleMatch propMatch = match("AttributeCdd", "name", null, null);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(propMatch));

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockAttributeCandidates);
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
        final RuleMatch actionMatch = match("ResponsibilityCdd", "validate", "ClassCdd", "System");

        final ResponsibilityCdd action = new ResponsibilityCdd();
        when(mockResponsibilityCandidates.findOrCreate("validate", null, null, null)).thenReturn(action);

        // when
        final List<Candidate> result = classUnderTest.createCandidatesFrom(Collections.singletonList(actionMatch));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(action);
        verify(mockClassCandidates, never()).findOrCreate("System", null);
        verify(mockResponsibilityCandidates).findOrCreate("validate", null, null, null);
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
        final RuleMatch propMatch = match("AttributeCdd", "description", "ClassCdd", "Condition");

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

    /**
     * The production list configures {@code UC} and {@code UseUC} in upper case, but the candidate
     * name is lower-cased before the lookup — comparing the configured entry verbatim matched
     * nothing, so {@code UC} was never actually blocked (and became a {@code ClassCdd} named
     * {@code UC}).
     */
    @Test
    void isBlockedUseCaseNoun_matchesAConfiguredUpperCaseEntry() {
        // given
        when(mockNlpProperties.getUseCaseBlockedNouns()).thenReturn(List.of("UC", "UseUC"));

        // expect
        assertThat(classUnderTest.isBlockedUseCaseNoun("UC")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("uc")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("UseUC")).isTrue();
        assertThat(classUnderTest.isBlockedUseCaseNoun("Customer")).isFalse();
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
        assertThat(classUnderTest.inferType("membershipFee")).isEqualTo("double");
        assertThat(classUnderTest.inferType("deliveryCost")).isEqualTo("double");
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

    /** A typed dependency in the given sentence — the link that makes two matches sentence-siblings. */
    private static TypedDependency dependencyIn(final Sentence sentence) {
        final TypedDependency dependency = new TypedDependency();
        dependency.setSentence(sentence);
        return dependency;
    }

    /** {@code nsubj(governor, dependent)} in the given sentence — an operation's actor (its owner). */
    private static TypedDependency actorDependency(
            final Sentence sentence, final String governor, final String dependent) {
        final TypedDependency dependency = dependencyIn(sentence);
        dependency.setType(TdType.NSUBJ);
        dependency.setGovernorLemma(governor);
        dependency.setGovernorPos(PartOfSpeechType.VB);
        dependency.setDependentLemma(dependent);
        dependency.setDependentPos(PartOfSpeechType.NN);
        return dependency;
    }

    /** {@code dobj(governor, dependent)} in the given sentence — an operation's object parameter. */
    private static TypedDependency objectDependency(
            final Sentence sentence, final String governor, final String dependent) {
        final TypedDependency dependency = dependencyIn(sentence);
        dependency.setType(TdType.OBJ);
        dependency.setGovernorLemma(governor);
        dependency.setGovernorPos(PartOfSpeechType.VB);
        dependency.setDependentLemma(dependent);
        dependency.setDependentPos(PartOfSpeechType.NN);
        return dependency;
    }

    /** A multiplicity match exactly as {@code TDR25}/{@code TDR26} record one. */
    private static RuleMatch multiplicityMatch(
            final String ruleClassName, final String candidateName, final String token, final Sentence sentence) {
        final TypedDependency dependency = dependencyIn(sentence);
        dependency.setGovernorLemma(candidateName.toLowerCase(Locale.ROOT));
        dependency.setDependentLemma(token);

        final RuleMatch match = new RuleMatch();
        match.setRuleClassName(ruleClassName);
        match.setCandidateType("ClassCdd");
        match.setCandidateName(candidateName);
        match.setTypedDependency(dependency);
        return match;
    }

    /**
     * A sentence with an id: the factory groups a document's matches by sentence id, and {@code id} is
     * generated by the database (no setter), so a unit test has to assign it reflectively.
     */
    private static Sentence sentenceWithId(final long id) {
        final Sentence sentence = new Sentence();
        setId(sentence, id);
        return sentence;
    }

    private static void setId(final Object entity, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (final Exception e) {
            throw new RuntimeException("failed to assign the id of " + entity, e);
        }
    }
}
