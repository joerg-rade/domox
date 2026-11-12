package domox.dom.crc.diagram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the hand-rolled JSON string escaping shared by the lexical diagram renderers.
 *
 * <p>A lemma or a display name is an arbitrary ontology string, so a quote, a backslash or a
 * control character in it must not be able to break the JSON payload.  The escaping used to be
 * duplicated in each renderer and had to be byte-identical everywhere.</p>
 */
class DiagramJsonTest {

    @Test
    void null_maps_to_the_empty_string() {
        assertThat(DiagramJson.escape(null)).isEmpty();
    }

    @Test
    void the_empty_string_maps_to_the_empty_string() {
        assertThat(DiagramJson.escape("")).isEmpty();
    }

    @Test
    void ordinary_text_passes_through_unchanged() {
        assertThat(DiagramJson.escape("Pet Shop")).isEqualTo("Pet Shop");
        assertThat(DiagramJson.escape("naïve café")).isEqualTo("naïve café");
    }

    @Test
    void a_backslash_is_doubled() {
        assertThat(DiagramJson.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void a_double_quote_is_escaped() {
        assertThat(DiagramJson.escape("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
    }

    @Test
    void a_newline_is_escaped() {
        assertThat(DiagramJson.escape("a\nb")).isEqualTo("a\\nb");
    }

    @Test
    void a_carriage_return_is_escaped() {
        assertThat(DiagramJson.escape("a\rb")).isEqualTo("a\\rb");
    }

    @Test
    void a_tab_is_escaped() {
        assertThat(DiagramJson.escape("a\tb")).isEqualTo("a\\tb");
    }

    @Test
    void a_backspace_is_escaped() {
        assertThat(DiagramJson.escape("a\bb")).isEqualTo("a\\bb");
    }

    @Test
    void a_form_feed_is_escaped() {
        assertThat(DiagramJson.escape("a\fb")).isEqualTo("a\\fb");
    }

    @Test
    void other_control_characters_use_the_unicode_form() {
        assertThat(DiagramJson.escape("a\u0001b")).isEqualTo("a\\u0001b");
        assertThat(DiagramJson.escape("a\u001fb")).isEqualTo("a\\u001fb");
    }

    @Test
    void the_space_is_the_first_code_point_that_passes_through() {
        // 0x20 is NOT a control character, so it must survive unescaped
        assertThat(DiagramJson.escape(" ")).isEqualTo(" ");
        assertThat(DiagramJson.escape("a b")).isEqualTo("a b");
    }

    @Test
    void the_unicode_form_is_lower_case_hex_zero_padded_to_four_digits() {
        assertThat(DiagramJson.escape("\u0000")).isEqualTo("\\u0000");
        assertThat(DiagramJson.escape("\u000b")).isEqualTo("\\u000b");
        assertThat(DiagramJson.escape("\u001a")).isEqualTo("\\u001a");
    }

    @Test
    void mixed_content_escapes_only_what_it_must() {
        assertThat(DiagramJson.escape("a\\b\"c\nd")).isEqualTo("a\\\\b\\\"c\\nd");
    }

    @Test
    void every_quote_and_backslash_in_the_output_is_preceded_by_a_backslash() {
        final String escaped = DiagramJson.escape("x\"y\\z");
        assertThat(escaped).isEqualTo("x\\\"y\\\\z");
        assertThat(escaped).hasSize(7);
    }
}
