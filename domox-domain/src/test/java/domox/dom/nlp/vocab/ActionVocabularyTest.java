package domox.dom.nlp.vocab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the action vocabulary — the action verbs and the service nouns, whose two registries
 * previously lived as static fields on {@code TypedDependencyPredicates}.
 *
 * <p>Both sets are independent, and both register and look up case-insensitively.</p>
 */
class ActionVocabularyTest {

    @BeforeEach
    void setUp() {
        ActionVocabulary.reset();
    }

    @Test
    void both_sets_start_empty() {
        assertThat(ActionVocabulary.containsActionVerb("offer")).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isFalse();
    }

    @Test
    void a_registered_action_verb_is_recognised() {
        ActionVocabulary.registerActionVerbs(Set.of("offer", "browse"));
        assertThat(ActionVocabulary.containsActionVerb("offer")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb("browse")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb("purchase")).isFalse();
    }

    @Test
    void a_registered_service_noun_is_recognised() {
        ActionVocabulary.registerServiceNouns(Set.of("grooming", "boarding"));
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("boarding")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("offer")).isFalse();
    }

    @Test
    void the_two_sets_are_independent() {
        ActionVocabulary.registerActionVerbs(Set.of("offer"));
        ActionVocabulary.registerServiceNouns(Set.of("grooming"));

        assertThat(ActionVocabulary.containsActionVerb("offer")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb("grooming")).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("offer")).isFalse();
    }

    @Test
    void both_lookups_are_case_insensitive() {
        ActionVocabulary.registerActionVerbs(Set.of("Offer"));
        ActionVocabulary.registerServiceNouns(Set.of("Grooming"));

        assertThat(ActionVocabulary.containsActionVerb("offer")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb("OFFER")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("GROOMING")).isTrue();
    }

    @Test
    void null_collections_are_noops() {
        ActionVocabulary.registerActionVerbs(null);
        ActionVocabulary.registerServiceNouns(null);

        assertThat(ActionVocabulary.containsActionVerb("offer")).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isFalse();
    }

    @Test
    void null_elements_are_skipped() {
        ActionVocabulary.registerActionVerbs(Arrays.asList("offer", null));
        ActionVocabulary.registerServiceNouns(Arrays.asList("grooming", null));

        assertThat(ActionVocabulary.containsActionVerb("offer")).isTrue();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb(null)).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun(null)).isFalse();
    }

    @Test
    void a_null_lemma_is_never_a_member() {
        ActionVocabulary.registerActionVerbs(Set.of("offer"));
        ActionVocabulary.registerServiceNouns(Set.of("grooming"));

        assertThat(ActionVocabulary.containsActionVerb(null)).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun(null)).isFalse();
    }

    @Test
    void registration_is_additive() {
        ActionVocabulary.registerActionVerbs(Set.of("offer"));
        ActionVocabulary.registerActionVerbs(Set.of("browse"));

        assertThat(ActionVocabulary.containsActionVerb("offer")).isTrue();
        assertThat(ActionVocabulary.containsActionVerb("browse")).isTrue();
    }

    @Test
    void reset_clears_both_sets() {
        ActionVocabulary.registerActionVerbs(Set.of("offer"));
        ActionVocabulary.registerServiceNouns(Set.of("grooming"));

        ActionVocabulary.reset();

        assertThat(ActionVocabulary.containsActionVerb("offer")).isFalse();
        assertThat(ActionVocabulary.containsServiceNoun("grooming")).isFalse();
    }
}
