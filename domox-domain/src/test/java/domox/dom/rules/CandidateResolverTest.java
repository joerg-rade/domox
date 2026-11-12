package domox.dom.rules;

import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guardrail for {@link CandidateResolver} — late-binding candidate resolution (Option C).
 * <p>
 * The resolver decides whether a noun seen in both entity-like and attribute-like positions should
 * stay a {@link ClassCdd}, become an {@link AttributeCdd}, or both.  The two scoring methods and the
 * name-inference helpers are package-private, so they are exercised directly; the public
 * {@link CandidateResolver#resolve(DomainModel, List)} entry point is tested with mocked
 * collaborators to pin <em>when</em> a property is created and when the pass skips a noun.
 */
@ExtendWith(MockitoExtension.class)
class CandidateResolverTest {

    @Mock AttributeCandidates attributeCandidates;
    @Mock BasicAttributeCatalog basicAttributeCatalog;
    @InjectMocks CandidateResolver resolver;

    // ---------------------------------------------------------------- helpers

    private static TypedDependency td(final TdType type, final String a, final String b) {
        final TypedDependency dependency = new TypedDependency();
        dependency.setType(type);
        dependency.setGovernorLemma(a);
        dependency.setDependentLemma(b);
        return dependency;
    }

    private static RuleMatch match(final TypedDependency dependency) {
        final RuleMatch ruleMatch = new RuleMatch();
        ruleMatch.setTypedDependency(dependency);
        ruleMatch.setRuleClassName("SomeRule");
        ruleMatch.setCandidateType("ClassCdd");
        ruleMatch.setCandidateName("c");
        return ruleMatch;
    }

    private static ClassCdd classCandidate(final String name, final DomainModel model) {
        final ClassCdd candidate = new ClassCdd();
        candidate.setCandidateName(name);
        candidate.setCandidateType("ClassCdd");
        candidate.domainModel = model;
        return candidate;
    }

    // ---------------------------------------------------------------- scoreEvidence

    @Test
    void nsubjOfAnActionVerbIsStrongEntityEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.NSUBJ, "buy", "pet"))), "pet");

        assertThat(score.entityScore()).isEqualTo(3);
        assertThat(score.attributeScore()).isZero();
        assertThat(score.dominant()).isEqualTo(CandidateResolver.EvidenceDominant.ENTITY);
    }

    @Test
    void nsubjOfACopularVerbIsOnlyWeakEntityEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.NSUBJ, "is", "pet"))), "pet");

        assertThat(score.entityScore()).isEqualTo(1);
    }

    @Test
    void passiveAndXsubjSubjectsAreEntityEvidenceToo() {
        final CandidateResolver.EvidenceScore score = resolver.scoreEvidence(List.of(
                match(td(TdType.NSUBJPASS, "buy", "pet")),
                match(td(TdType.NSUBJ_XSUBJ, "buy", "pet"))), "pet");

        assertThat(score.entityScore()).isEqualTo(6);
    }

    @Test
    void aCompoundHeadNounIsEntityEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.COMPOUND, "pet", "store"))), "pet");

        assertThat(score.entityScore()).isEqualTo(2);
        assertThat(score.attributeScore()).isZero();
    }

    @Test
    void anObjectOfAnActionVerbIsEntityEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.OBJ, "buy", "pet"))), "pet");

        assertThat(score.entityScore()).isEqualTo(1);
    }

    @Test
    void aCompoundDependentIsStrongAttributeEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.COMPOUND, "store", "pet"))), "pet");

        assertThat(score.attributeScore()).isEqualTo(3);
        assertThat(score.entityScore()).isZero();
        assertThat(score.dominant()).isEqualTo(CandidateResolver.EvidenceDominant.ATTRIBUTE);
    }

    @Test
    void somethingOfTheNounIsStrongAttributeEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.NMOD_OF, "colour", "pet"))), "pet");

        assertThat(score.attributeScore()).isEqualTo(3);
    }

    @Test
    void theNounOfSomethingIsWeakAttributeEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.NMOD_OF, "pet", "store"))), "pet");

        assertThat(score.attributeScore()).isEqualTo(1);
    }

    @Test
    void aPossessiveGovernorIsAttributeEvidence() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.NMOD_POSS, "pet", "owner"))), "pet");

        assertThat(score.attributeScore()).isEqualTo(2);
    }

    @Test
    void anObjectNamedLikeABasicAttributeIsAttributeEvidence() {
        when(basicAttributeCatalog.contains("age")).thenReturn(true);

        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.OBJ, "age", "pet"))), "pet");

        // "age" is not a copular verb, so the obj branch also scores weak entity evidence
        assertThat(score.attributeScore()).isEqualTo(2);
        assertThat(score.entityScore()).isEqualTo(1);
    }

    @Test
    void aDependencyNamedLikeABasicAttributeIsAttributeEvidence() {
        when(basicAttributeCatalog.contains("age")).thenReturn(true);

        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(TdType.DEP, "age", "pet"))), "pet");

        assertThat(score.attributeScore()).isEqualTo(2);
        assertThat(score.entityScore()).isZero();
    }

    @Test
    void matchesWithoutATypedDependencyAreIgnored() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(new RuleMatch()), "pet");

        assertThat(score.entityScore()).isZero();
        assertThat(score.attributeScore()).isZero();
    }

    @Test
    void dependenciesWithoutATypeAreIgnored() {
        final CandidateResolver.EvidenceScore score =
                resolver.scoreEvidence(List.of(match(td(null, "buy", "pet"))), "pet");

        assertThat(score.entityScore()).isZero();
        assertThat(score.attributeScore()).isZero();
    }

    @Test
    void evidenceDominanceIsDecidedByTheLargerScore() {
        assertThat(new CandidateResolver.EvidenceScore(3, 1).dominant())
                .isEqualTo(CandidateResolver.EvidenceDominant.ENTITY);
        assertThat(new CandidateResolver.EvidenceScore(1, 3).dominant())
                .isEqualTo(CandidateResolver.EvidenceDominant.ATTRIBUTE);
        assertThat(new CandidateResolver.EvidenceScore(2, 2).dominant())
                .isEqualTo(CandidateResolver.EvidenceDominant.BALANCED);
    }

    // ---------------------------------------------------------------- inferOwningClass

    @Test
    void theRelatedClassCandidateNameWinsWhenPresent() {
        final RuleMatch ruleMatch = match(td(TdType.COMPOUND, "store", "pet"));
        ruleMatch.setRelatedCandidateName("Shop");
        ruleMatch.setRelatedCandidateType("ClassCdd");

        assertThat(resolver.inferOwningClass(List.of(ruleMatch), "pet")).isEqualTo("Shop");
    }

    @Test
    void aCompoundGovernorNamesTheOwningClass() {
        assertThat(resolver.inferOwningClass(List.of(match(td(TdType.COMPOUND, "store", "pet"))), "pet"))
                .isEqualTo("Store");
    }

    @Test
    void anNmodOfGovernorNamesTheOwningClass() {
        assertThat(resolver.inferOwningClass(List.of(match(td(TdType.NMOD_OF, "colour", "pet"))), "pet"))
                .isEqualTo("Colour");
    }

    @Test
    void aPossessiveDependentNamesTheOwningClass() {
        assertThat(resolver.inferOwningClass(List.of(match(td(TdType.NMOD_POSS, "pet", "owner"))), "pet"))
                .isEqualTo("Owner");
    }

    @Test
    void withNoStructureTheNounItselfIsTheOwningClass() {
        assertThat(resolver.inferOwningClass(List.of(match(td(TdType.DET, "a", "pet"))), "pet"))
                .isEqualTo("Pet");
    }

    // ---------------------------------------------------------------- name inference

    @ParameterizedTest
    @CsvSource({
            "orderCount, int",
            "itemNumber, int",
            "personAge,   int",
            "totalPrice,  double",
            "netAmount,   double",
            "isActive,    boolean",
            "isValid,     boolean",
            "isEnabled,   boolean",
            "orderDate,   LocalDateTime",
            "startTime,   LocalDateTime",
            "name,        String"
    })
    void propertyTypesAreInferredFromTheName(final String name, final String expected) {
        assertThat(CandidateResolver.inferPropertyType(name)).isEqualTo(expected);
    }

    @Test
    void aNullPropertyNameIsAString() {
        assertThat(CandidateResolver.inferPropertyType(null)).isEqualTo("String");
    }

    @ParameterizedTest
    @CsvSource({"buy, true", "have, false", "is, false", "do, false", "BUY, true"})
    void copularAndAuxiliaryVerbsAreNotActionVerbs(final String lemma, final boolean expected) {
        assertThat(CandidateResolver.isActionVerb(lemma)).isEqualTo(expected);
    }

    @Test
    void aNullLemmaIsNotAnActionVerb() {
        assertThat(CandidateResolver.isActionVerb(null)).isFalse();
    }

    @Test
    void capitalizeUpperCasesOnlyTheFirstLetter() {
        assertThat(CandidateResolver.capitalize("pet")).isEqualTo("Pet");
        assertThat(CandidateResolver.capitalize("")).isEmpty();
        assertThat(CandidateResolver.capitalize(null)).isNull();
    }

    // ---------------------------------------------------------------- resolve

    @Test
    void anAttributeDominatedNounGainsAPropertyBoundToItsOwningClass() {
        final DomainModel model = new DomainModel();
        final ClassCdd pet = classCandidate("pet", model);
        final RuleMatch ruleMatch = match(td(TdType.COMPOUND, "store", "pet"));
        pet.setRuleMatches(List.of(ruleMatch));

        final AttributeCdd created = new AttributeCdd();
        when(attributeCandidates.findOrCreate("Store", "pet", "String", model)).thenReturn(created);

        resolver.resolve(model, List.of(pet));

        assertThat(created.getRuleMatches()).containsExactly(ruleMatch);
    }

    @Test
    void aNounOnTheBasicAttributeWhitelistIsNeverTurnedIntoAProperty() {
        final DomainModel model = new DomainModel();
        final ClassCdd pet = classCandidate("pet", model);
        pet.setRuleMatches(List.of(match(td(TdType.COMPOUND, "store", "pet"))));
        when(basicAttributeCatalog.contains("pet")).thenReturn(true);

        resolver.resolve(model, List.of(pet));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aNounThatAlreadyHasAPropertyIsSkipped() {
        final DomainModel model = new DomainModel();
        final ClassCdd owner = classCandidate("store", model);
        final AttributeCdd existing = new AttributeCdd("pet", "String");
        existing.classCdd = owner;
        when(attributeCandidates.listAll()).thenReturn(List.of(existing));

        final ClassCdd pet = classCandidate("pet", model);
        pet.setRuleMatches(List.of(match(td(TdType.COMPOUND, "store", "pet"))));

        resolver.resolve(model, List.of(pet));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aClassWithNoRuleMatchesIsSkipped() {
        final DomainModel model = new DomainModel();
        final ClassCdd pet = classCandidate("pet", model);

        resolver.resolve(model, List.of(pet));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void anEntityDominatedNounIsNotTurnedIntoAProperty() {
        final DomainModel model = new DomainModel();
        final ClassCdd pet = classCandidate("pet", model);
        pet.setRuleMatches(List.of(match(td(TdType.NSUBJ, "buy", "pet"))));

        resolver.resolve(model, List.of(pet));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aClassFromAnotherDomainModelIsIgnored() {
        final DomainModel model = new DomainModel();
        final ClassCdd pet = classCandidate("pet", new DomainModel());
        pet.setRuleMatches(List.of(match(td(TdType.COMPOUND, "store", "pet"))));

        resolver.resolve(model, List.of(pet));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aClassWithNoNameIsIgnored() {
        final DomainModel model = new DomainModel();
        final ClassCdd unnamed = classCandidate(null, model);
        unnamed.setRuleMatches(List.of(match(td(TdType.COMPOUND, "store", "pet"))));

        resolver.resolve(model, List.of(unnamed));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void nonClassCandidatesAreIgnored() {
        final DomainModel model = new DomainModel();

        resolver.resolve(model, List.of(new AttributeCdd("stray", "String")));

        verify(attributeCandidates, never()).findOrCreate(anyString(), anyString(), anyString(), any());
    }
}
