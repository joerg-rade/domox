package domox.dom.crc.diagram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Verifies the Penn POS code → fill colour mapping shared by the D3-style renderers.
 *
 * <p>The colour must be identical everywhere, because the client legend and the rendered nodes are
 * coloured from the same payload; a divergence would show a node under the wrong legend entry.</p>
 */
class PosPaletteTest {

    @Test
    void nouns_are_blue() {
        assertThat(PosPalette.color("NN")).isEqualTo("#3498DB");
        assertThat(PosPalette.color("NNS")).isEqualTo("#3498DB");
    }

    @Test
    void proper_nouns_are_light_blue() {
        assertThat(PosPalette.color("NNP")).isEqualTo("#85C1E9");
        assertThat(PosPalette.color("NNPS")).isEqualTo("#85C1E9");
    }

    @Test
    void verbs_are_red() {
        for (final String code : new String[]{"VB", "VBZ", "VBD", "VBG", "VBN", "VBP"}) {
            assertThat(PosPalette.color(code)).as(code).isEqualTo("#E74C3C");
        }
    }

    @Test
    void adjectives_are_green() {
        for (final String code : new String[]{"JJ", "JJR", "JJS"}) {
            assertThat(PosPalette.color(code)).as(code).isEqualTo("#2ECC71");
        }
    }

    @Test
    void determiners_are_orange() {
        assertThat(PosPalette.color("DT")).isEqualTo("#F39C12");
    }

    @Test
    void prepositions_are_teal() {
        assertThat(PosPalette.color("IN")).isEqualTo("#17A2B8");
    }

    @Test
    void adverbs_are_yellow() {
        assertThat(PosPalette.color("RB")).isEqualTo("#FFBB28");
    }

    @Test
    void cardinal_numbers_are_gold() {
        assertThat(PosPalette.color("CD")).isEqualTo("#F1C40F");
    }

    @Test
    void coordinating_conjunctions_are_turquoise() {
        assertThat(PosPalette.color("CC")).isEqualTo("#00C49F");
    }

    @Test
    void pronouns_and_wh_words_are_purple() {
        for (final String code : new String[]{"PRP", "PRP$", "WDT", "WP", "WP$", "WRB"}) {
            assertThat(PosPalette.color(code)).as(code).isEqualTo("#9B59B6");
        }
    }

    @Test
    void punctuation_is_white() {
        for (final String code : new String[]{",", ".", "!", "?", ";", ":"}) {
            assertThat(PosPalette.color(code)).as(code).isEqualTo("#FFFFFF");
        }
    }

    @Test
    void an_unknown_code_falls_back_to_grey() {
        assertThat(PosPalette.color("XYZ")).isEqualTo("#AAB7B8");
        assertThat(PosPalette.color("")).isEqualTo("#AAB7B8");
        assertThat(PosPalette.color("FW")).isEqualTo("#AAB7B8");
    }

    @Test
    void the_lookup_is_case_sensitive() {
        // "nn" is not the Penn code "NN" — it falls through to the grey default
        assertThat(PosPalette.color("nn")).isEqualTo("#AAB7B8");
    }

    @Test
    void the_lookup_is_exact_and_not_a_prefix_match() {
        assertThat(PosPalette.color("NNX")).isEqualTo("#AAB7B8");
        assertThat(PosPalette.color("VBZZ")).isEqualTo("#AAB7B8");
    }

    @Test
    void a_null_code_is_rejected() {
        // the switch on a String selector throws on null; callers pass firstPos(...) which is never null
        assertThatNullPointerException().isThrownBy(() -> PosPalette.color(null));
    }
}
