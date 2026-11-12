package domox.dom.crc.diagram;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Verifies the shared POS legend: the ordered category table, the code→label and label→colour
 * lookups (including the catch-all fall-through), and the {@code "legend"} JSON emission.
 *
 * <p>The table used to be duplicated across {@link CandidateD3Graph}, {@link DomainModelDiagramsD3}
 * and {@link CandidateVegaGraph}; these tests pin the behaviour all three relied on, so the three
 * cannot drift apart again.</p>
 */
class PosLegendTest {

    // ---------------------------------------------------------------- the table

    @Test
    void entries_are_ordered_by_semantic_category_and_end_with_the_catch_all() {
        assertThat(PosLegend.ENTRIES).hasSize(9);
        assertThat(PosLegend.ENTRIES.getFirst().label()).isEqualTo("Noun");
        assertThat(PosLegend.ENTRIES.getLast().label()).isEqualTo("Other");
        assertThat(PosLegend.ENTRIES.getLast().posCodes()).isEmpty();
    }

    @Test
    void only_the_catch_all_entry_has_no_pos_codes() {
        assertThat(PosLegend.ENTRIES)
                .filteredOn(e -> e.posCodes().isEmpty())
                .extracting(PosLegend.Entry::label)
                .containsExactly("Other");
    }

    // ---------------------------------------------------------------- label

    @Test
    void label_maps_a_plural_noun_to_its_category() {
        assertThat(PosLegend.label("NN")).isEqualTo("Noun");
        assertThat(PosLegend.label("NNS")).isEqualTo("Noun");
    }

    @Test
    void label_maps_every_verb_form_to_the_verb_category() {
        for (final String code : new String[]{"VB", "VBZ", "VBD", "VBG", "VBN", "VBP"}) {
            assertThat(PosLegend.label(code)).as(code).isEqualTo("Verb");
        }
    }

    @Test
    void label_falls_through_to_the_catch_all_for_an_uncovered_code() {
        // CD and CC have their own colour in PosPalette but no legend category
        assertThat(PosLegend.label("CD")).isEqualTo("Other");
        assertThat(PosLegend.label("CC")).isEqualTo("Other");
        assertThat(PosLegend.label("XYZ")).isEqualTo("Other");
        assertThat(PosLegend.label("")).isEqualTo("Other");
    }

    @Test
    void label_is_case_sensitive_and_exact() {
        assertThat(PosLegend.label("nn")).isEqualTo("Other");
        assertThat(PosLegend.label("NNX")).isEqualTo("Other");
    }

    @Test
    void label_rejects_a_null_code() {
        // callers pass DiagramLemmas.firstPos(...), which is never null; the immutable POS sets
        // reject a null lookup, exactly as the per-renderer copies did
        assertThatNullPointerException().isThrownBy(() -> PosLegend.label(null));
    }

    // ---------------------------------------------------------------- colorForLabel

    @Test
    void colorForLabel_returns_the_entry_colour() {
        assertThat(PosLegend.colorForLabel("Noun")).isEqualTo("#3498DB");
        assertThat(PosLegend.colorForLabel("Other")).isEqualTo("#AAB7B8");
    }

    @Test
    void colorForLabel_falls_back_to_the_catch_all_colour() {
        assertThat(PosLegend.colorForLabel("No such category")).isEqualTo("#AAB7B8");
    }

    // ---------------------------------------------------------------- appendLegendJson

    @Test
    void appendLegendJson_is_empty_when_nothing_is_used() {
        final StringBuilder json = new StringBuilder();
        PosLegend.appendLegendJson(json, Set.of());
        assertThat(json.toString()).isEqualTo("\"legend\":[]");
    }

    @Test
    void appendLegendJson_emits_only_the_used_category() {
        final StringBuilder json = new StringBuilder();
        PosLegend.appendLegendJson(json, Set.of("NN"));
        assertThat(json.toString()).isEqualTo(
                "\"legend\":[{\"color\":\"#3498DB\",\"label\":\"Noun\","
                        + "\"description\":\"Entities and objects (NN, NNS)\"}]");
    }

    @Test
    void appendLegendJson_emits_the_catch_all_for_an_uncovered_code() {
        final StringBuilder json = new StringBuilder();
        PosLegend.appendLegendJson(json, Set.of("CD"));
        assertThat(json.toString()).isEqualTo(
                "\"legend\":[{\"color\":\"#AAB7B8\",\"label\":\"Other\","
                        + "\"description\":\"Other parts of speech\"}]");
    }

    @Test
    void appendLegendJson_keeps_the_table_order_and_appends_the_catch_all_last() {
        final StringBuilder json = new StringBuilder();
        // "CD" is uncovered, so the catch-all joins "Verb" and "Noun" — in table order.
        PosLegend.appendLegendJson(json, usedPos("NN", "CD", "VBZ"));
        assertThat(json.toString()).isEqualTo(
                "\"legend\":[{\"color\":\"#3498DB\",\"label\":\"Noun\",\"description\":\"Entities and objects (NN, NNS)\"},"
                        + "{\"color\":\"#E74C3C\",\"label\":\"Verb\",\"description\":\"Actions and predicates (VB, VBZ)\"},"
                        + "{\"color\":\"#AAB7B8\",\"label\":\"Other\",\"description\":\"Other parts of speech\"}]");
    }

    @Test
    void appendLegendJson_omits_the_catch_all_when_every_used_code_is_covered() {
        final StringBuilder json = new StringBuilder();
        PosLegend.appendLegendJson(json, usedPos("NN", "VB"));
        assertThat(json.toString()).doesNotContain("\"Other\"");
        assertThat(json.toString()).contains("\"Noun\"").contains("\"Verb\"");
    }

    private static Set<String> usedPos(final String... codes) {
        return new LinkedHashSet<>(java.util.List.of(codes));
    }
}
