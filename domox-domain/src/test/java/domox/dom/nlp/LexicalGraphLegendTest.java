package domox.dom.nlp;

import domox.dom.crc.diagram.PosPalette;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the DOT diagram's legend and POS palette.  The palette must agree with the
 * D3 renderers' {@link PosPalette} (and the adapter's PlantUML generator) — the legend's colour rows
 * are the swatches a reader uses to decode the node fills, so a drift there is a real defect.
 */
class LexicalGraphLegendTest {

    @Test
    void thePaletteAgreesWithTheD3RenderersPosPalette() {
        for (final String code : new String[]{
                "NN", "NNS", "NNP", "NNPS", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ",
                "JJ", "JJR", "JJS", "DT", "PRP", "PRP$", "IN", "RB", "CD", "CC",
                "WDT", "WP", "WP$", "WRB", ",", ".", "!", "?", ";", ":", "FW", "XYZ", ""}) {
            assertThat(LexicalGraphLegend.color(code))
                    .as("POS %s", code)
                    .isEqualTo(PosPalette.color(code));
        }
    }

    @Test
    void anUnknownPosFallsBackToGrey() {
        assertThat(LexicalGraphLegend.color("XYZ")).isEqualTo("#AAB7B8");
    }

    @Test
    void everyLegendColourRowUsesTheColourItsPosCodesAreFilledWith() {
        for (int i = 0; i < LexicalGraphLegend.colorRowCount(); i++) {
            final String[] row = LexicalGraphLegend.colorRow(i);
            assertThat(row).as("row %d is {swatch, label, description}", i).hasSize(3);
            assertThat(LexicalGraphLegend.color(codeForRow(i)))
                    .as("row %d swatch matches the node fill", i)
                    .isEqualTo(row[0]);
        }
    }

    @Test
    void theLegendHasTheFourPosFamilyRows() {
        assertThat(LexicalGraphLegend.colorRowCount()).isEqualTo(4);
        assertThat(LexicalGraphLegend.colorRow(0)[1]).isEqualTo("Noun");
        assertThat(LexicalGraphLegend.colorRow(1)[1]).isEqualTo("Proper noun");
        assertThat(LexicalGraphLegend.colorRow(2)[1]).isEqualTo("Verb");
        assertThat(LexicalGraphLegend.colorRow(3)[1]).isEqualTo("Adjective");
    }

    @Test
    void appendsTheLegendAsAPlainHtmlLikeNode() {
        final StringBuilder dot = new StringBuilder();
        LexicalGraphLegend.append(dot);
        final String legend = dot.toString();

        assertThat(legend).contains("Legend [");
        assertThat(legend).contains("shape = plain");
        assertThat(legend).contains("Node Type Legend");
        assertThat(legend).contains("Node size");
        assertThat(legend).contains("Dashed border");
        assertThat(legend).contains("Bold border");
        assertThat(legend).contains("Bold + dashed");
        assertThat(legend).contains("bgcolor=\"#3498DB\"");
        assertThat(legend).contains("bgcolor=\"#85C1E9\"");
        assertThat(legend).contains("bgcolor=\"#E74C3C\"");
        assertThat(legend).contains("bgcolor=\"#2ECC71\"");
        assertThat(legend).as("no pinned pos — corner placement is the renderer's job").doesNotContain("pos =");
        assertThat(legend).doesNotContain("rank = sink");
    }

    /** A representative POS code for legend row {@code i}. */
    private static String codeForRow(final int i) {
        return switch (i) {
            case 0 -> "NN";
            case 1 -> "NNP";
            case 2 -> "VB";
            default -> "JJ";
        };
    }
}
