package domox.dom.crc.diagram;

import domox.dom.AbstractEntity;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the context-matching rule — {@link ContextMatching#wordParts} (splitting a name into its
 * words) and {@link ContextMatching#occurrences} (which sentences carry all of them) — with no
 * container, no repository and no database: the look-up the rule needs is passed in as a plain
 * {@link Function}, exactly as {@code ContextEvidence} passes
 * {@code TypedDependencyRepository#findByLemma}.
 *
 * <p>What is pinned here is the <em>rule</em> (a compound name matches only a sentence carrying
 * <em>every</em> one of its words) and what an occurrence carries (the sentence's id, text, POS,
 * match count, and the surface forms the sentence matched on — the words the table highlights).
 * The container half of the same feature — the JPQL and the sentence rows the candidate page shows —
 * is guarded by {@code ContextEvidenceIntegTest}.</p>
 */
class ContextMatchingTest {

    // ============ wordParts ============

    @Test
    void wordParts_splitsACamelCaseNameAtItsCaseBoundary() {
        assertThat(ContextMatching.wordParts("PetShop")).containsExactly("pet", "shop");
        assertThat(ContextMatching.wordParts("OnlineStore")).containsExactly("online", "store");
    }

    @Test
    void wordParts_splitsOnNonAlphanumericSeparatorsAndSkipsConsecutiveOnes() {
        assertThat(ContextMatching.wordParts("TD2-Reader")).containsExactly("td2", "reader");
        assertThat(ContextMatching.wordParts("a_b")).containsExactly("a", "b");
        assertThat(ContextMatching.wordParts("Pet__Shop")).containsExactly("pet", "shop");
        assertThat(ContextMatching.wordParts(" Pet Shop ")).containsExactly("pet", "shop");
    }

    @Test
    void wordParts_keepsARunOfCapitalsTogether() {
        assertThat(ContextMatching.wordParts("PDFViewer")).containsExactly("pdf", "viewer");
        assertThat(ContextMatching.wordParts("TD2")).containsExactly("td2");
        assertThat(ContextMatching.wordParts("AStore")).containsExactly("a", "store");
        assertThat(ContextMatching.wordParts("Pet2Shop")).containsExactly("pet2", "shop");
        assertThat(ContextMatching.wordParts("OnlineStore")).containsExactly("online", "store");
    }

    @Test
    void wordParts_keepsADigitRunWithItsLetters() {
        assertThat(ContextMatching.wordParts("Uc01")).containsExactly("uc01");
        assertThat(ContextMatching.wordParts("2ndShop")).containsExactly("2nd", "shop");
    }

    @Test
    void wordParts_ofNothingIsEmpty() {
        assertThat(ContextMatching.wordParts(null)).isEmpty();
        assertThat(ContextMatching.wordParts("")).isEmpty();
        assertThat(ContextMatching.wordParts("---")).isEmpty();
    }

    @Test
    void wordParts_ofASingleWordLowercasesIt() {
        assertThat(ContextMatching.wordParts("Customer")).containsExactly("customer");
        assertThat(ContextMatching.wordParts("customer")).containsExactly("customer");
    }

    // ============ occurrences — the matching rule ============

    @Test
    void occurrences_requiresEveryPartOfACompoundNameToMatch() {
        // given — sentence 3 carries only "pet", sentence 7 carries both "pet" and "shop"
        final Sentence onlyPet = sentence(3L, "The pet is groomed.");
        final Sentence both = sentence(7L, "The pet shop sells pet food.");
        final Map<String, List<TypedDependency>> byLemma = Map.of(
                "pet", List.of(dependency(onlyPet, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN),
                        dependency(both, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN)),
                "shop", List.of(dependency(both, "sell", PartOfSpeechType.VBZ, "shop", PartOfSpeechType.NN)));

        // when
        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet", "shop"), lookup(byLemma));

        // then — the partial match is dropped
        assertThat(occurrences).extracting(ContextMatching.SentenceOccurrence::id).containsExactly(7L);
    }

    @Test
    void occurrences_areSortedAscendingBySentenceIdWhateverOrderTheLookupReturns() {
        final Sentence high = sentence(9L, "Pet and shop.");
        final Sentence low = sentence(2L, "Shop and pet.");
        final Map<String, List<TypedDependency>> byLemma = Map.of(
                "pet", List.of(dependency(high, "and", PartOfSpeechType.CC, "pet", PartOfSpeechType.NN),
                        dependency(low, "and", PartOfSpeechType.CC, "pet", PartOfSpeechType.NN)),
                "shop", List.of(dependency(high, "and", PartOfSpeechType.CC, "shop", PartOfSpeechType.NN),
                        dependency(low, "and", PartOfSpeechType.CC, "shop", PartOfSpeechType.NN)));

        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet", "shop"), lookup(byLemma));

        assertThat(occurrences).extracting(ContextMatching.SentenceOccurrence::id).containsExactly(2L, 9L);
    }

    @Test
    void occurrences_countEveryMatchingDependencyAndTakeThePosOfTheLastMatchedPart() {
        // given — "pet" matches twice (NN), "shop" once (NNP)
        final Sentence sentence = sentence(4L, "The pet shop: pet food.");
        final List<TypedDependency> petDeps = List.of(
                dependency(sentence, "shop", PartOfSpeechType.NN, "pet", PartOfSpeechType.NN),
                dependency(sentence, "food", PartOfSpeechType.NN, "pet", PartOfSpeechType.NN));
        final Map<String, List<TypedDependency>> byLemma = Map.of(
                "pet", petDeps,
                "shop", List.of(dependency(sentence, "sell", PartOfSpeechType.VBZ, "shop", PartOfSpeechType.NNP)));

        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet", "shop"), lookup(byLemma));

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().count()).as("one per matching dependency").isEqualTo(3);
        assertThat(occurrences.getFirst().pos()).as("POS of the head word of the compound").isEqualTo("NNP");
        assertThat(occurrences.getFirst().text()).isEqualTo(sentence.getText());
    }

    @Test
    void occurrences_readThePosOffWhicheverEndpointCarriesTheLemma() {
        // given — the part is the *governor* here, so the governor's POS must be the one reported
        final Sentence sentence = sentence(1L, "Pet care.");
        final Map<String, List<TypedDependency>> byLemma = Map.of(
                "pet", List.of(dependency(sentence, "pet", PartOfSpeechType.NNS, "care", PartOfSpeechType.NN)));

        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet"), lookup(byLemma));

        assertThat(occurrences.getFirst().pos()).isEqualTo("NNS");
    }

    @Test
    void occurrences_ignoreDependenciesWithoutAUsableSentence() {
        final Sentence orphan = new Sentence();                       // no id assigned yet
        final Sentence real = sentence(6L, "The pet.");
        final List<TypedDependency> deps = new ArrayList<>();
        deps.add(null);
        deps.add(dependency(null, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN));
        deps.add(dependency(orphan, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN));
        deps.add(dependency(real, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN));

        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet"), lookup(Map.of("pet", deps)));

        assertThat(occurrences).extracting(ContextMatching.SentenceOccurrence::id).containsExactly(6L);
    }

    @Test
    void occurrences_tolerateALookupThatKnowsNothingAboutAPart() {
        // the look-up answers null — the rule must not throw
        assertThat(ContextMatching.occurrences(List.of("pet"), lemma -> null)).isEmpty();
    }

    @Test
    void occurrences_ofNoPartsIsEmpty() {
        assertThat(ContextMatching.occurrences(null, lemma -> List.of())).isEmpty();
        assertThat(ContextMatching.occurrences(List.of(), lemma -> List.of())).isEmpty();
    }

    @Test
    void occurrences_ofAPartThatMatchesNowhereIsEmpty() {
        final Sentence sentence = sentence(1L, "The pet.");

        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("shop"), lookup(Map.of("pet",
                        List.of(dependency(sentence, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN)))));

        assertThat(occurrences).isEmpty();
    }

    // ============ occurrences — the surface forms the sentence matched on ============

    @Test
    void occurrences_reportTheSurfaceFormsTheSentenceMatchedOn() {
        // given — the lemmas are the candidate's words, but the sentence writes them differently
        final Sentence sentence = sentence(5L, "Pets and shops.");
        final TypedDependency asDependent =
                dependency(sentence, "and", PartOfSpeechType.CC, "pet", PartOfSpeechType.NN);
        asDependent.setDependentGloss("Pets");
        final TypedDependency asGovernor =
                dependency(sentence, "shop", PartOfSpeechType.NN, "and", PartOfSpeechType.CC);
        asGovernor.setGovernorGloss("shops");
        final Map<String, List<TypedDependency>> byLemma = Map.of(
                "pet", List.of(asDependent),
                "shop", List.of(asGovernor));

        // when
        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet", "shop"), lookup(byLemma));

        // then — the words as written, in the order the parts of the name matched
        assertThat(occurrences.getFirst().terms()).containsExactly("Pets", "shops");
    }

    @Test
    void occurrences_reportEachSurfaceFormOnce() {
        // given — one part, matched by two dependencies that write it differently
        final Sentence sentence = sentence(3L, "The pet grooms pets.");
        final TypedDependency first =
                dependency(sentence, "groom", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN);
        first.setDependentGloss("pet");
        final TypedDependency second =
                dependency(sentence, "groom", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NNS);
        second.setDependentGloss("pets");
        final Map<String, List<TypedDependency>> byLemma = Map.of("pet", List.of(first, second));

        // when
        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet"), lookup(byLemma));

        // then — both words are highlighted once, and the repeated gloss is not listed twice
        assertThat(occurrences.getFirst().terms()).containsExactly("pet", "pets");
    }

    @Test
    void occurrences_ofADependencyWithoutAGlossReportNoTerm() {
        // the gloss is the word the sentence writes; without one the rule invents nothing, so the
        // sentence is shown unhighlighted rather than highlighted in the wrong place
        final Sentence sentence = sentence(2L, "A pet is groomed.");
        final Map<String, List<TypedDependency>> byLemma = Map.of("pet",
                List.of(dependency(sentence, "groom", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN)));

        // when
        final List<ContextMatching.SentenceOccurrence> occurrences =
                ContextMatching.occurrences(List.of("pet"), lookup(byLemma));

        // then
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().terms()).isEmpty();
    }

    // ============ fixtures ============

    /** A sentence with a database id, assigned reflectively as the sequence generator would. */
    private static Sentence sentence(final long id, final String text) {
        final Sentence sentence = new Sentence();
        sentence.setText(text);
        setId(sentence, id);
        return sentence;
    }

    /** A typed dependency in {@code sentence}, with the given lemmas and POS codes. */
    private static TypedDependency dependency(final Sentence sentence,
                                              final String governorLemma, final PartOfSpeechType governorPos,
                                              final String dependentLemma, final PartOfSpeechType dependentPos) {
        final TypedDependency td = new TypedDependency();
        td.setSentence(sentence);
        td.setGovernorLemma(governorLemma);
        td.setGovernorPos(governorPos);
        td.setDependentLemma(dependentLemma);
        td.setDependentPos(dependentPos);
        return td;
    }

    /** The look-up {@code occurrences} takes, backed by a lemma → dependencies map. */
    private static Function<String, List<TypedDependency>> lookup(final Map<String, List<TypedDependency>> byLemma) {
        final Map<String, List<TypedDependency>> copy = new LinkedHashMap<>(byLemma);
        return lemma -> copy.getOrDefault(lemma, List.of());
    }

    /** Assigns the private JPA {@code id} field (normally set by the sequence generator). */
    private static void setId(final AbstractEntity entity, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (final Exception e) {
            throw new IllegalStateException("failed to set id on " + entity, e);
        }
    }
}
