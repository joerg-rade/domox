package domox.dom.nlp.vocab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the synonym-marker vocabulary — the predicate verbs of a defining construction such as
 * "X, also known as Y" — which previously lived as a static field on
 * {@code TypedDependencyPredicates}.
 *
 * <p>Registration and lookup are case-insensitive.</p>
 */
class SynonymVocabularyTest {

    @BeforeEach
    void setUp() {
        SynonymVocabulary.reset();
    }

    @Test
    void the_registry_starts_empty() {
        assertThat(SynonymVocabulary.contains("mean")).isFalse();
    }

    @Test
    void a_registered_marker_is_recognised() {
        SynonymVocabulary.register(Set.of("mean", "call"));
        assertThat(SynonymVocabulary.contains("mean")).isTrue();
        assertThat(SynonymVocabulary.contains("call")).isTrue();
        assertThat(SynonymVocabulary.contains("name")).isFalse();
    }

    @Test
    void the_lookup_is_case_insensitive() {
        SynonymVocabulary.register(Set.of("Mean"));
        assertThat(SynonymVocabulary.contains("Mean")).isTrue();
        assertThat(SynonymVocabulary.contains("mean")).isTrue();
        assertThat(SynonymVocabulary.contains("MEAN")).isTrue();
    }

    @Test
    void a_null_collection_is_a_noop() {
        SynonymVocabulary.register(null);
        assertThat(SynonymVocabulary.contains("mean")).isFalse();
    }

    @Test
    void a_null_element_is_skipped() {
        SynonymVocabulary.register(Arrays.asList("mean", null));
        assertThat(SynonymVocabulary.contains("mean")).isTrue();
        assertThat(SynonymVocabulary.contains(null)).isFalse();
    }

    @Test
    void a_null_lemma_is_never_a_member() {
        SynonymVocabulary.register(Set.of("mean"));
        assertThat(SynonymVocabulary.contains(null)).isFalse();
    }

    @Test
    void registration_is_additive() {
        SynonymVocabulary.register(Set.of("mean"));
        SynonymVocabulary.register(Set.of("call"));
        assertThat(SynonymVocabulary.contains("mean")).isTrue();
        assertThat(SynonymVocabulary.contains("call")).isTrue();
    }

    @Test
    void reset_clears_the_registry() {
        SynonymVocabulary.register(Set.of("mean"));
        assertThat(SynonymVocabulary.contains("mean")).isTrue();

        SynonymVocabulary.reset();

        assertThat(SynonymVocabulary.contains("mean")).isFalse();
    }
}
