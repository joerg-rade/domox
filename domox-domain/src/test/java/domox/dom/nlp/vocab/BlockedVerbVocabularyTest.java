package domox.dom.nlp.vocab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the blocked-verb vocabulary — the lemmas that must never be treated as an action verb.
 *
 * <p>Registration and lookup are case-insensitive (the registry lower-cases on both sides).</p>
 */
class BlockedVerbVocabularyTest {

    @BeforeEach
    void setUp() {
        BlockedVerbVocabulary.reset();
    }

    @Test
    void the_registry_starts_empty() {
        assertThat(BlockedVerbVocabulary.contains("have")).isFalse();
    }

    @Test
    void a_registered_verb_is_recognised() {
        BlockedVerbVocabulary.register(Set.of("have", "use"));
        assertThat(BlockedVerbVocabulary.contains("have")).isTrue();
        assertThat(BlockedVerbVocabulary.contains("use")).isTrue();
        assertThat(BlockedVerbVocabulary.contains("browse")).isFalse();
    }

    @Test
    void the_lookup_is_case_insensitive() {
        BlockedVerbVocabulary.register(Set.of("Have"));
        assertThat(BlockedVerbVocabulary.contains("Have")).isTrue();
        assertThat(BlockedVerbVocabulary.contains("have")).isTrue();
        assertThat(BlockedVerbVocabulary.contains("HAVE")).isTrue();
    }

    @Test
    void a_null_collection_is_a_noop() {
        BlockedVerbVocabulary.register(null);
        assertThat(BlockedVerbVocabulary.contains("have")).isFalse();
    }

    @Test
    void a_null_element_is_skipped() {
        BlockedVerbVocabulary.register(Arrays.asList("have", null));
        assertThat(BlockedVerbVocabulary.contains("have")).isTrue();
        assertThat(BlockedVerbVocabulary.contains(null)).isFalse();
    }

    @Test
    void a_null_lemma_is_never_a_member() {
        BlockedVerbVocabulary.register(Set.of("have"));
        assertThat(BlockedVerbVocabulary.contains(null)).isFalse();
    }

    @Test
    void registration_is_additive() {
        BlockedVerbVocabulary.register(Set.of("have"));
        BlockedVerbVocabulary.register(Set.of("use"));
        assertThat(BlockedVerbVocabulary.contains("have")).isTrue();
        assertThat(BlockedVerbVocabulary.contains("use")).isTrue();
    }

    @Test
    void reset_clears_the_registry() {
        BlockedVerbVocabulary.register(Set.of("have"));
        assertThat(BlockedVerbVocabulary.contains("have")).isTrue();

        BlockedVerbVocabulary.reset();

        assertThat(BlockedVerbVocabulary.contains("have")).isFalse();
    }
}
