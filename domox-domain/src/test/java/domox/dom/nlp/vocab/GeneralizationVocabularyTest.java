package domox.dom.nlp.vocab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the four generalization vocabularies — copular "be" verbs, indefinite articles,
 * kind/type/sort terms and stop adjectives — which previously lived as four static fields on
 * {@code TypedDependencyPredicates}.
 *
 * <p>The registries share one registrar and one lifecycle, so they are grouped into one class; the
 * tests below also pin that the individual resets are independent and that
 * {@link GeneralizationVocabulary#resetAll()} clears all four.</p>
 */
class GeneralizationVocabularyTest {

    @BeforeEach
    void setUp() {
        GeneralizationVocabulary.resetAll();
    }

    @Test
    void all_four_registries_start_empty() {
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isFalse();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isFalse();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isFalse();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isFalse();
    }

    // ---------------------------------------------------------------- be verbs

    @Test
    void a_registered_be_verb_is_recognised() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("is", "are", "was"));
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb("are")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb("was")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb("browse")).isFalse();
    }

    @Test
    void the_be_verb_lookup_is_case_insensitive() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("Is"));
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb("IS")).isTrue();
    }

    @Test
    void a_null_be_verb_is_never_a_member() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("is"));
        assertThat(GeneralizationVocabulary.containsBeVerb(null)).isFalse();
    }

    @Test
    void a_null_be_verb_collection_is_a_noop() {
        GeneralizationVocabulary.registerBeVerbs(null);
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isFalse();
    }

    // ---------------------------------------------------------------- indefinite articles

    @Test
    void a_registered_article_is_recognised() {
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("a", "an"));
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isTrue();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("an")).isTrue();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("the")).isFalse();
    }

    @Test
    void the_article_lookup_is_case_insensitive() {
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("An"));
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("an")).isTrue();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("AN")).isTrue();
    }

    @Test
    void a_null_article_is_never_a_member() {
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("a"));
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle(null)).isFalse();
    }

    @Test
    void a_null_article_collection_is_a_noop() {
        GeneralizationVocabulary.registerIndefiniteArticles(null);
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isFalse();
    }

    // ---------------------------------------------------------------- kind / type / sort terms

    @Test
    void a_registered_kind_type_sort_term_is_recognised() {
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("kind", "type", "sort"));
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("type")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("sort")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("pet")).isFalse();
    }

    @Test
    void the_kind_type_sort_test_is_a_substring_test() {
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("kind"));
        // a lemma merely CONTAINING the term counts — this is intentional (compound lemmas)
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("subkind")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kindofthing")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("unkind")).isTrue();
    }

    @Test
    void the_kind_type_sort_lookup_is_case_insensitive_on_both_sides() {
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("Kind"));
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("KIND")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("SubKind")).isTrue();
    }

    @Test
    void a_null_lemma_has_no_kind_type_or_sort_term() {
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("kind"));
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort(null)).isFalse();
    }

    @Test
    void a_null_kind_type_sort_collection_is_a_noop() {
        GeneralizationVocabulary.registerKindTypeSortTerms(null);
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isFalse();
    }

    // ---------------------------------------------------------------- stop adjectives

    @Test
    void a_registered_stop_adjective_is_recognised() {
        GeneralizationVocabulary.registerStopAdjectives(Set.of("good", "bad"));
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("bad")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("green")).isFalse();
    }

    @Test
    void the_stop_adjective_lookup_is_case_insensitive() {
        GeneralizationVocabulary.registerStopAdjectives(Set.of("Good"));
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("GOOD")).isTrue();
    }

    @Test
    void a_null_stop_adjective_is_never_a_member() {
        GeneralizationVocabulary.registerStopAdjectives(Set.of("good"));
        assertThat(GeneralizationVocabulary.containsStopAdjective(null)).isFalse();
    }

    @Test
    void a_null_stop_adjective_collection_is_a_noop() {
        GeneralizationVocabulary.registerStopAdjectives(null);
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isFalse();
    }

    @Test
    void null_elements_are_skipped_by_every_registrar() {
        GeneralizationVocabulary.registerBeVerbs(Arrays.asList("is", null));
        GeneralizationVocabulary.registerIndefiniteArticles(Arrays.asList("a", null));
        GeneralizationVocabulary.registerKindTypeSortTerms(Arrays.asList("kind", null));
        GeneralizationVocabulary.registerStopAdjectives(Arrays.asList("good", null));

        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isTrue();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb(null)).isFalse();
    }

    // ---------------------------------------------------------------- resets

    @Test
    void resetAll_clears_all_four_registries() {
        registerOneOfEach();
        assertAllFourRegistered();

        GeneralizationVocabulary.resetAll();

        assertAllFourEmpty();
    }

    @Test
    void each_individual_reset_touches_only_its_own_registry() {
        registerOneOfEach();

        GeneralizationVocabulary.resetBeVerbs();
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isFalse();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();

        GeneralizationVocabulary.resetIndefiniteArticles();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isFalse();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();

        GeneralizationVocabulary.resetKindTypeSortTerms();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isFalse();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();

        GeneralizationVocabulary.resetStopAdjectives();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isFalse();
    }

    @Test
    void the_registries_do_not_leak_into_one_another() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("kind"));
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("kind"));
        GeneralizationVocabulary.registerStopAdjectives(Set.of("kind"));

        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isFalse();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("unkind")).isFalse();
    }

    @Test
    void registration_is_additive() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("is"));
        GeneralizationVocabulary.registerBeVerbs(Set.of("are"));
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isTrue();
        assertThat(GeneralizationVocabulary.containsBeVerb("are")).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private void registerOneOfEach() {
        GeneralizationVocabulary.registerBeVerbs(Set.of("is"));
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("a"));
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("kind"));
        GeneralizationVocabulary.registerStopAdjectives(Set.of("good"));
    }

    private void assertAllFourRegistered() {
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isTrue();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isTrue();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isTrue();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isTrue();
    }

    private void assertAllFourEmpty() {
        assertThat(GeneralizationVocabulary.containsBeVerb("is")).isFalse();
        assertThat(GeneralizationVocabulary.containsIndefiniteArticle("a")).isFalse();
        assertThat(GeneralizationVocabulary.containsKindTypeOrSort("kind")).isFalse();
        assertThat(GeneralizationVocabulary.containsStopAdjective("good")).isFalse();
    }
}
