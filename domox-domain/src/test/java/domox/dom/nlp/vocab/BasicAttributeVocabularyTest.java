package domox.dom.nlp.vocab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the basic-attribute vocabulary, which owns the registry that previously lived as a
 * static field on {@code TypedDependencyPredicates}.
 *
 * <p>Unlike the other vocabularies this one is <em>verbatim</em>: the caller registers already
 * lower-cased lemmas and the membership test is case-sensitive.</p>
 */
class BasicAttributeVocabularyTest {

    @BeforeEach
    void setUp() {
        BasicAttributeVocabulary.reset();
    }

    @Test
    void the_registry_starts_empty() {
        assertThat(BasicAttributeVocabulary.contains("name")).isFalse();
    }

    @Test
    void a_registered_lemma_is_recognised() {
        BasicAttributeVocabulary.register(Set.of("name", "number"));
        assertThat(BasicAttributeVocabulary.contains("name")).isTrue();
        assertThat(BasicAttributeVocabulary.contains("number")).isTrue();
        assertThat(BasicAttributeVocabulary.contains("address")).isFalse();
    }

    @Test
    void registration_and_lookup_are_verbatim_case_sensitive() {
        BasicAttributeVocabulary.register(Set.of("Name"));
        assertThat(BasicAttributeVocabulary.contains("Name")).isTrue();
        assertThat(BasicAttributeVocabulary.contains("name")).isFalse();
    }

    @Test
    void a_null_collection_is_a_noop() {
        BasicAttributeVocabulary.register(null);
        assertThat(BasicAttributeVocabulary.contains("name")).isFalse();
    }

    @Test
    void a_null_lemma_is_never_a_member() {
        BasicAttributeVocabulary.register(Arrays.asList("name", null));
        assertThat(BasicAttributeVocabulary.contains("name")).isTrue();
        assertThat(BasicAttributeVocabulary.contains(null)).isFalse();
    }

    @Test
    void registration_is_additive() {
        BasicAttributeVocabulary.register(Set.of("name"));
        BasicAttributeVocabulary.register(Set.of("number"));
        assertThat(BasicAttributeVocabulary.contains("name")).isTrue();
        assertThat(BasicAttributeVocabulary.contains("number")).isTrue();
    }

    @Test
    void reset_clears_the_registry() {
        BasicAttributeVocabulary.register(Set.of("name"));
        assertThat(BasicAttributeVocabulary.contains("name")).isTrue();

        BasicAttributeVocabulary.reset();

        assertThat(BasicAttributeVocabulary.contains("name")).isFalse();
    }
}
