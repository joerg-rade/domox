package domox.dom.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the DOT literal syntax.  An unquoted or unescaped id makes Graphviz/Kroki
 * reject the whole graph with HTTP 400, so every id the generator emits goes through here.
 */
class DotLiteralsTest {

    @Test
    void quotesAnIdAndEscapesBackslashAndQuote() {
        assertThat(DotLiterals.quoted("item_nn")).isEqualTo("\"item_nn\"");
        assertThat(DotLiterals.quoted("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(DotLiterals.quoted("a\\b")).isEqualTo("\"a\\\\b\"");
    }

    @Test
    void escapesNullToTheEmptyString() {
        assertThat(DotLiterals.escape(null)).isEmpty();
        assertThat(DotLiterals.quoted(null)).isEqualTo("\"\"");
    }

    @Test
    void aNodeLabelCarriesThePosInGuillemetsOnASecondLine() {
        assertThat(DotLiterals.nodeLabel("item", "NN")).isEqualTo("\"item\\n«NN»\"");
    }

    @Test
    void aNodeLabelWithoutAPosIsSingleLine() {
        assertThat(DotLiterals.nodeLabel("item", "")).isEqualTo("\"item\"");
    }

    @Test
    void aNodeLabelEscapesItsLemma() {
        assertThat(DotLiterals.nodeLabel("a\"b", "NN")).isEqualTo("\"a\\\"b\\n«NN»\"");
    }

    @Test
    void formatsDimensionsWithThreeDecimals() {
        assertThat(DotLiterals.formatDouble(0.9)).isEqualTo("0.900");
        assertThat(DotLiterals.formatDouble(3.6)).isEqualTo("3.600");
        assertThat(DotLiterals.formatDouble(0)).isEqualTo("0.000");
    }
}
