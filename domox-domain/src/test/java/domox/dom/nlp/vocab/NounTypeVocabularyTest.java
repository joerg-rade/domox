package domox.dom.nlp.vocab;

import domox.dom.rules.NounType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the noun-type lexicon — lemma → {@link NounType} — that previously lived as a static
 * field on {@code TypedDependencyPredicates}.
 *
 * <p>Keys are stored and looked up lower-cased, so the lexicon is case-insensitive.</p>
 */
class NounTypeVocabularyTest {

    @BeforeEach
    void setUp() {
        NounTypeVocabulary.reset();
    }

    @Test
    void the_lexicon_starts_empty() {
        assertThat(NounTypeVocabulary.typeOf("name")).isNull();
    }

    @Test
    void a_registered_lemma_maps_to_its_type() {
        NounTypeVocabulary.register("name", NounType.GENERIC_ATTRIBUTE);
        NounTypeVocabulary.register("customer", NounType.ROLE_NOUN);
        NounTypeVocabulary.register("order", NounType.DOMAIN_ENTITY);

        assertThat(NounTypeVocabulary.typeOf("name")).isEqualTo(NounType.GENERIC_ATTRIBUTE);
        assertThat(NounTypeVocabulary.typeOf("customer")).isEqualTo(NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf("order")).isEqualTo(NounType.DOMAIN_ENTITY);
    }

    @Test
    void an_unregistered_lemma_is_null() {
        NounTypeVocabulary.register("name", NounType.GENERIC_ATTRIBUTE);
        assertThat(NounTypeVocabulary.typeOf("address")).isNull();
    }

    @Test
    void the_lookup_is_case_insensitive() {
        NounTypeVocabulary.register("Customer", NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf("Customer")).isEqualTo(NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf("customer")).isEqualTo(NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf("CUSTOMER")).isEqualTo(NounType.ROLE_NOUN);
    }

    @Test
    void re_registering_the_same_lemma_overwrites_the_type() {
        NounTypeVocabulary.register("order", NounType.ABSTRACT_CONCEPT);
        assertThat(NounTypeVocabulary.typeOf("order")).isEqualTo(NounType.ABSTRACT_CONCEPT);

        NounTypeVocabulary.register("ORDER", NounType.DOMAIN_ENTITY);

        assertThat(NounTypeVocabulary.typeOf("order")).isEqualTo(NounType.DOMAIN_ENTITY);
    }

    @Test
    void a_null_lemma_registration_is_a_noop() {
        NounTypeVocabulary.register(null, NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf(null)).isNull();
    }

    @Test
    void a_null_type_registration_is_a_noop() {
        NounTypeVocabulary.register("customer", null);
        assertThat(NounTypeVocabulary.typeOf("customer")).isNull();
    }

    @Test
    void a_null_lemma_lookup_is_null() {
        NounTypeVocabulary.register("customer", NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf(null)).isNull();
    }

    @Test
    void every_NounType_value_can_be_registered_and_read_back() {
        for (final NounType type : NounType.values()) {
            final String lemma = type.name();
            NounTypeVocabulary.register(lemma, type);
            assertThat(NounTypeVocabulary.typeOf(lemma)).isEqualTo(type);
        }
    }

    @Test
    void reset_clears_the_lexicon() {
        NounTypeVocabulary.register("customer", NounType.ROLE_NOUN);
        assertThat(NounTypeVocabulary.typeOf("customer")).isEqualTo(NounType.ROLE_NOUN);

        NounTypeVocabulary.reset();

        assertThat(NounTypeVocabulary.typeOf("customer")).isNull();
    }
}
