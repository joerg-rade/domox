package domox.dom.crc.diagram;

import domox.dom.AbstractEntity;
import domox.dom.crc.model.ClassCdd;
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
 * Verifies the two pure halves of the context graph — the matching rule
 * ({@link ContextGraphJson#wordParts}, {@link ContextGraphJson#occurrences}) and the D3 payload
 * ({@link ContextGraphJson#render}) — plus the small text/type helpers they use.
 *
 * <p>No container, no repository, no database: the look-up the rule needs is passed in as a plain
 * {@link Function}, exactly as {@code ContextGraphs} passes
 * {@code TypedDependencyRepository#findByLemma}.  What is pinned here is the <em>rule</em> (a
 * compound name matches only a sentence carrying <em>every</em> one of its words) and the
 * <em>contract</em> the shared client relies on (hub id {@code candidate}, sentence ids
 * {@code sentence:<id>}, one hub→sentence link each, click-through only when both
 * {@code candidateId} and {@code objectType} are present).</p>
 */
class ContextGraphJsonTest {

    // ============ wordParts ============

    @Test
    void wordParts_splitsACamelCaseNameAtItsCaseBoundary() {
        assertThat(ContextGraphJson.wordParts("PetShop")).containsExactly("pet", "shop");
        assertThat(ContextGraphJson.wordParts("OnlineStore")).containsExactly("online", "store");
    }

    @Test
    void wordParts_splitsOnNonAlphanumericSeparatorsAndSkipsConsecutiveOnes() {
        assertThat(ContextGraphJson.wordParts("TD2-Reader")).containsExactly("td2", "reader");
        assertThat(ContextGraphJson.wordParts("a_b")).containsExactly("a", "b");
        assertThat(ContextGraphJson.wordParts("Pet__Shop")).containsExactly("pet", "shop");
        assertThat(ContextGraphJson.wordParts(" Pet Shop ")).containsExactly("pet", "shop");
    }

    @Test
    void wordParts_keepsARunOfCapitalsTogether() {
        assertThat(ContextGraphJson.wordParts("PDFViewer")).containsExactly("pdf", "viewer");
        assertThat(ContextGraphJson.wordParts("TD2")).containsExactly("td2");
        assertThat(ContextGraphJson.wordParts("AStore")).containsExactly("a", "store");
        assertThat(ContextGraphJson.wordParts("Pet2Shop")).containsExactly("pet2", "shop");
        assertThat(ContextGraphJson.wordParts("OnlineStore")).containsExactly("online", "store");
    }

    @Test
    void wordParts_keepsADigitRunWithItsLetters() {
        assertThat(ContextGraphJson.wordParts("Uc01")).containsExactly("uc01");
        assertThat(ContextGraphJson.wordParts("2ndShop")).containsExactly("2nd", "shop");
    }

    @Test
    void wordParts_ofNothingIsEmpty() {
        assertThat(ContextGraphJson.wordParts(null)).isEmpty();
        assertThat(ContextGraphJson.wordParts("")).isEmpty();
        assertThat(ContextGraphJson.wordParts("---")).isEmpty();
    }

    @Test
    void wordParts_ofASingleWordLowercasesIt() {
        assertThat(ContextGraphJson.wordParts("Customer")).containsExactly("customer");
        assertThat(ContextGraphJson.wordParts("customer")).containsExactly("customer");
    }

    // ============ truncate ============

    @Test
    void truncate_stripsButKeepsAShortTextIntact() {
        assertThat(ContextGraphJson.truncate("  The pet is groomed.  ")).isEqualTo("The pet is groomed.");
    }

    @Test
    void truncate_ofNothingIsEmpty() {
        assertThat(ContextGraphJson.truncate(null)).isEmpty();
        assertThat(ContextGraphJson.truncate("   ")).isEmpty();
    }

    @Test
    void truncate_cutsAtTheLastBlankThatFitsAndAppendsAnEllipsis() {
        final String text = "a".repeat(ContextGraphJson.LABEL_MAX_CHARS) + " tail";

        final String truncated = ContextGraphJson.truncate(text);

        assertThat(truncated).hasSize(ContextGraphJson.LABEL_MAX_CHARS + 1);
        assertThat(truncated).endsWith("\u2026");
        assertThat(truncated).doesNotContain("tail");
    }

    @Test
    void truncate_hardCutsWhenThereIsNoBlankToBreakAt() {
        final String text = "a".repeat(ContextGraphJson.LABEL_MAX_CHARS + 10);

        final String truncated = ContextGraphJson.truncate(text);

        assertThat(truncated).isEqualTo("a".repeat(ContextGraphJson.LABEL_MAX_CHARS) + "\u2026");
    }

    @Test
    void truncate_keepsATextOfExactlyTheLimit() {
        final String text = "a".repeat(ContextGraphJson.LABEL_MAX_CHARS);

        assertThat(ContextGraphJson.truncate(text)).isEqualTo(text);
    }

    // ============ objectTypeOf ============

    @Test
    void objectTypeOf_readsTheNamedAnnotationOfTheEntity() {
        assertThat(ContextGraphJson.objectTypeOf(Sentence.class)).isEqualTo("domox.Sentence");
        assertThat(ContextGraphJson.objectTypeOf(ClassCdd.class)).isEqualTo("domox.ClassCdd");
    }

    @Test
    void objectTypeOf_walksUpToAnInheritedNamedAnnotation() {
        // A subclass of an @Named entity is not itself annotated, so the type must be found above it.
        assertThat(ContextGraphJson.objectTypeOf(SubClassCdd.class)).isEqualTo("domox.ClassCdd");
    }

    @Test
    void objectTypeOf_fallsBackToTheNamespacedSimpleNameWhenNothingIsAnnotated() {
        assertThat(ContextGraphJson.objectTypeOf(Unannotated.class)).isEqualTo("domox.Unannotated");
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
        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("pet", "shop"), lookup(byLemma));

        // then — the partial match is dropped
        assertThat(occurrences).extracting(ContextGraphJson.SentenceOccurrence::id).containsExactly(7L);
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

        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("pet", "shop"), lookup(byLemma));

        assertThat(occurrences).extracting(ContextGraphJson.SentenceOccurrence::id).containsExactly(2L, 9L);
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

        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("pet", "shop"), lookup(byLemma));

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

        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("pet"), lookup(byLemma));

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

        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("pet"), lookup(Map.of("pet", deps)));

        assertThat(occurrences).extracting(ContextGraphJson.SentenceOccurrence::id).containsExactly(6L);
    }

    @Test
    void occurrences_tolerateALookupThatKnowsNothingAboutAPart() {
        // the look-up answers null — the rule must not throw
        assertThat(ContextGraphJson.occurrences(List.of("pet"), lemma -> null)).isEmpty();
    }

    @Test
    void occurrences_ofNoPartsIsEmpty() {
        assertThat(ContextGraphJson.occurrences(null, lemma -> List.of())).isEmpty();
        assertThat(ContextGraphJson.occurrences(List.of(), lemma -> List.of())).isEmpty();
    }

    @Test
    void occurrences_ofAPartThatMatchesNowhereIsEmpty() {
        final Sentence sentence = sentence(1L, "The pet.");

        final List<ContextGraphJson.SentenceOccurrence> occurrences =
                ContextGraphJson.occurrences(List.of("shop"), lookup(Map.of("pet",
                        List.of(dependency(sentence, "sell", PartOfSpeechType.VBZ, "pet", PartOfSpeechType.NN)))));

        assertThat(occurrences).isEmpty();
    }

    // ============ render — the payload contract ============

    @Test
    void render_ofNoCenterIsNoPayload() {
        assertThat(ContextGraphJson.render(null, "domox.Sentence", List.of())).isNull();
    }

    @Test
    void render_emitsTheHubWithItsFixedIdAndItsObjectReference() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", true),
                "domox.Sentence", List.of());

        assertThat(json).startsWith("{").endsWith("}");
        assertContains(json, "\"id\":\"" + ContextGraphJson.HUB_NODE_ID + "\"");
        assertContains(json, "\"name\":\"PetShop\"");
        assertContains(json, "\"approved\":true");
        assertContains(json, "\"isCandidateColumn\":true");
        assertContains(json, "\"candidateId\":42");
        assertContains(json, "\"objectType\":\"domox.ClassCdd\"");
        assertContains(json, "\"r\":" + ContextGraphJson.HUB_RADIUS);
    }

    @Test
    void render_emitsOneNodeAndOneLinkPerOccurrence() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(7L, "The pet shop.", "NNP", 2)));

        assertContains(json, "\"id\":\"" + ContextGraphJson.SENTENCE_NODE_ID_PREFIX + "7\"");
        assertContains(json, "\"name\":\"The pet shop.\"");
        assertContains(json, "\"objectType\":\"domox.Sentence\"");
        assertContains(json, "\"candidateId\":7", "a sentence node links to its own object page");
        assertContains(json, "\"source\":\"" + ContextGraphJson.HUB_NODE_ID + "\",\"target\":\""
                + ContextGraphJson.SENTENCE_NODE_ID_PREFIX + "7\"");
    }

    @Test
    void render_sizesASentenceNodeByItsMatchCount() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(1L, "Few.", "NN", 1),
                        new ContextGraphJson.SentenceOccurrence(2L, "Many.", "NN", 9)));

        // the most-matched sentence gets the largest radius the shared sizing produces, the
        // least-matched one the base radius — i.e. the count really drives the node size
        assertContains(json, "\"r\":" + DiagramNodeSizing.radiusFor(9, 9));
        assertContains(json, "\"r\":" + (int) DiagramNodeSizing.NODE_BASE_RADIUS);
    }

    @Test
    void render_ofNoOccurrencesIsALoneHubAndNoLinks() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence", List.of());

        assertContains(json, "\"links\":[]");
        assertDoesNotContain(json, "\"target\"");
        assertDoesNotContain(json, "\"id\":\"" + ContextGraphJson.SENTENCE_NODE_ID_PREFIX);
    }

    @Test
    void render_treatsANullOccurrenceListAsNone() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence", null);

        assertContains(json, "\"links\":[]");
    }

    @Test
    void render_makesTheHubClickThroughOnlyWhenItHasBothIdAndType() {
        // no id → nothing to open, so the node must not offer a click-through
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", null, "domox.ClassCdd", false),
                "domox.Sentence", List.of());

        assertContains(json, "\"isCandidateColumn\":false");
        assertDoesNotContain(json, "\"candidateId\"");
        assertDoesNotContain(json, "\"objectType\"");
    }

    @Test
    void render_usesTheDominantPosCodeForTheHubAndOnlyTheUsedCodesInTheLegend() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(1L, "The pet shop.", "NNP", 2),
                        new ContextGraphJson.SentenceOccurrence(2L, "A pet shop.", "NNP", 1)));

        assertContains(json, "\"pos\":\"NNP\"", "the hub takes the POS the sentences matched on");
        assertContains(json, "\"legend\":[");
        assertContains(json, "\"label\":\"Proper noun\"", "NNP is used, so its legend entry is listed");
        assertDoesNotContain(json, "\"label\":\"Verb\"", "no verb code is used, so no verb entry");
    }

    @Test
    void render_leavesTheHubPosBlankWhenNoOccurrenceCarriedOne() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(1L, "The pet shop.", "", 1)));

        assertContains(json, "\"pos\":\"\"");
    }

    @Test
    void render_truncatesALongSentenceLabel() {
        final String longText = "pet ".repeat(60);

        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("PetShop", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(1L, longText, "NN", 1)));

        assertContains(json, "\u2026");
        assertDoesNotContain(json, longText.strip());
    }

    @Test
    void render_escapesQuotesInTheNamesItEmits() {
        final String json = ContextGraphJson.render(
                new ContextGraphJson.Center("Pet \"Shop\"", 42L, "domox.ClassCdd", false),
                "domox.Sentence",
                List.of(new ContextGraphJson.SentenceOccurrence(1L, "A \"quoted\" pet shop.", "NN", 1)));

        assertContains(json, "\"name\":\"Pet \\\"Shop\\\"\"");
        assertContains(json, "A \\\"quoted\\\" pet shop.");
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

    // ============ assertion helpers ============

    private static void assertContains(final String json, final String substring) {
        assertThat(json).as("expected JSON to contain \"%s\"%nJSON: %s", substring, json).contains(substring);
    }

    private static void assertContains(final String json, final String substring, final String message) {
        assertThat(json).as("%s%nexpected \"%s\"%nJSON: %s", message, substring, json).contains(substring);
    }

    private static void assertDoesNotContain(final String json, final String substring) {
        assertThat(json).as("expected JSON NOT to contain \"%s\"%nJSON: %s", substring, json)
                .doesNotContain(substring);
    }

    private static void assertDoesNotContain(final String json, final String substring, final String message) {
        assertThat(json).as("%s%nexpected NOT \"%s\"%nJSON: %s", message, substring, json)
                .doesNotContain(substring);
    }

    // ============ fixture types ============

    /** A subclass of {@link ClassCdd}: carries no annotation of its own. */
    private static class SubClassCdd extends ClassCdd {

        SubClassCdd() {
            super("Pet", List.of(), List.of(), List.of());
        }
    }

    /** Nothing in this hierarchy is annotated — the fallback path of {@code objectTypeOf}. */
    private static class Unannotated {
    }
}
