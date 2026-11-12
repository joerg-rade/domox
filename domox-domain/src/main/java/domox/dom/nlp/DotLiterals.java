package domox.dom.nlp;

import java.util.Locale;

/**
 * Graphviz <b>DOT literal syntax</b>: quoting, escaping, node labels and number formatting.
 * <p>
 * Extracted from {@code LexicalGraphGenerator}, where these four one-line helpers sat among the graph
 * algorithm, the legend and the candidate-derived border styling.  They are the only place that knows
 * how to make arbitrary ontology text safe inside DOT — an unquoted id such as {@code data-center} or
 * the keyword {@code node} makes Graphviz/Kroki reject the graph with HTTP 400, which tears down the
 * whole list view — so keeping them together (and away from the layout logic) is deliberate.
 * <p>
 * Static and free of any framework dependency, so it is unit-testable on its own.
 */
public final class DotLiterals {

    private DotLiterals() {
    }

    /** A DOT double-quoted string/id literal with its content escaped for backslash and double-quote. */
    public static String quoted(final String raw) {
        return "\"" + escape(raw) + "\"";
    }

    /** Escapes backslashes and double-quotes so {@code raw} is safe inside a DOT quoted string/id. */
    public static String escape(final String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * DOT label literal for a node: the (escaped) lemma with its POS code as a second line in
     * guillemets. The literal {@code \n} line-break is deliberately left unescaped so Graphviz
     * renders a real line break, while the lemma/POS text itself is escaped for DOT string-literal
     * safety. {@code null} POS tags produce a bare single-line label.
     */
    public static String nodeLabel(final String lemma, final String pos) {
        final String leaf = escape(lemma);
        final String tag = escape(pos);
        return "\"" + (tag.isEmpty() ? leaf : leaf + "\\n«" + tag + "»") + "\"";
    }

    /** Formats a node dimension with three decimals (DOT width/height in inches). */
    public static String formatDouble(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
