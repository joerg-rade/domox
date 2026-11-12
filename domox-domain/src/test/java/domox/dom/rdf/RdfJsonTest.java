package domox.dom.rdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guardrail for the JSON string escaping shared by {@link RdfDiagramJson} and
 * {@link DiagramJsonWriter}.  A diagram's node names and labels are arbitrary ontology text, so an
 * unescaped quote or control character would produce unparseable JSON and tear down the whole view.
 */
class RdfJsonTest {

    @Test
    void escapesTheCharactersJsonRequires() {
        assertThat(RdfJson.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(RdfJson.escape("a\"b")).isEqualTo("a\\\"b");
        assertThat(RdfJson.escape("a\nb")).isEqualTo("a\\nb");
        assertThat(RdfJson.escape("a\rb")).isEqualTo("a\\rb");
        assertThat(RdfJson.escape("a\tb")).isEqualTo("a\\tb");
    }

    @Test
    void escapesOtherControlCharactersAsUnicode() {
        assertThat(RdfJson.escape("\u0001")).isEqualTo("\\u0001");
        assertThat(RdfJson.escape("\u001f")).isEqualTo("\\u001f");
    }

    @Test
    void leavesOrdinaryTextUntouched() {
        assertThat(RdfJson.escape("Feather Cat Toy")).isEqualTo("Feather Cat Toy");
        assertThat(RdfJson.escape("<<Class>>")).isEqualTo("<<Class>>");
        assertThat(RdfJson.escape("")).isEmpty();
    }

    @Test
    void mapsNullToTheEmptyString() {
        assertThat(RdfJson.escape(null)).isEmpty();
    }
}
