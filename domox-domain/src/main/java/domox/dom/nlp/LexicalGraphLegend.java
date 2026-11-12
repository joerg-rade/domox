package domox.dom.nlp;

/**
 * The <b>legend and POS colour palette of the lexical (DOT) diagram</b>: the table node appended to
 * every graph, and the Penn-tag → fill-colour mapping the node declarations use.
 * <p>
 * Extracted from {@code LexicalGraphGenerator} so the legend's HTML-like DOT markup and the colour
 * table live in one place; the two must agree (each legend row's swatch is the colour its POS codes
 * are filled with), and the palette must agree with the D3 renderers' {@code PosPalette} and the
 * adapter's {@code ColoredPlantUmlMindmapGenerator.getPosColor} — {@code LexicalGraphLegendTest}
 * pins that agreement.
 */
final class LexicalGraphLegend {

    /**
     * The legend's colour rows: a swatch fill colour paired with a short node-type label and a
     * description listing the POS tags that map to it (mirroring {@link #color(String)} so the
     * legend stays in sync with the node colours).  Each row fills the swatch / name / description
     * columns of the HTML-like legend table.
     */
    private static final String[][] COLOR_ROWS = {
            {"#3498DB", "Noun", "Entities and objects («NN», «NNS»)"},
            {"#85C1E9", "Proper noun", "Named entities («NNP», «NNPS»)"},
            {"#E74C3C", "Verb", "Actions and predicates («VB», «VBZ»)"},
            {"#2ECC71", "Adjective", "Descriptors and modifiers («JJ», «JJR», «JJS»)"}
    };

    private LexicalGraphLegend() {
    }

    /**
     * Maps a Penn POS tag to its fill colour — mirroring {@code PosPalette.color} (and the adapter's
     * {@code ColoredPlantUmlMindmapGenerator.getPosColor}) so the generators stay consistent.
     */
    static String color(final String pos) {
        return switch (pos) {
            case "NN", "NNS" -> "#3498DB";     // Blue for nouns
            case "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C";     // Red for verbs
            case "JJ", "JJR", "JJS" -> "#2ECC71";     // Green for adjectives
            case "DT" -> "#F39C12";     // Orange for determiners
            case "PRP", "PRP$" -> "#9B59B6";     // Purple for pronouns
            case "IN" -> "#17A2B8";     // Teal for prepositions
            case "RB" -> "#FFBB28";     // Amber for adverbs
            case "CD" -> "#F1C40F";     // Yellow for numerals
            case "CC" -> "#00C49F";     // Teal for coordinating conjunctions
            case "WDT", "WP", "WP$", "WRB" -> "#9B59B6";     // Soft purple for wh-words
            case "NNP", "NNPS" -> "#85C1E9";     // Light blue for proper nouns
            case ",", ".", "!", "?", ";", ":" -> "#FFFFFF";     // White for punctuation
            default -> "#AAB7B8";     // Neutral gray for others
        };
    }

    /** The number of colour rows in the legend (used by the guardrail test). */
    static int colorRowCount() {
        return COLOR_ROWS.length;
    }

    /** The {@code {swatchColour, label, description}} of legend colour row {@code i}. */
    static String[] colorRow(final int i) {
        return COLOR_ROWS[i];
    }

    /**
     * Appends the diagram's legend as a single HTML-like table node (the node-type legend
     * template): a bordered table with a header band and one row per visual rule, so the reader
     * can decode the node fill colour per part of speech, the node size (proportional to the
     * number of linked rules) and the border semantics (dashed = part of a synonym pair, bold =
     * approved candidate, bold + dashed = approved synonym member).  Drawn as a {@code plain}
     * node so the table renders inline; the node carries a pinned {@code pos} (the {@code !}
     * suffix) so neato keeps it out of the force model and parks it at the diagram's lower-left
     * corner.
     */
    static void append(final StringBuilder dot) {
        dot.append("\n    // ==========================================\n");
        dot.append("    // LEGEND (Table Layout using HTML-like node)\n");
        dot.append("    // ==========================================\n");
        dot.append("    Legend [\n");
        dot.append("        shape = plain\n");
        // The legend's corner placement is intentionally NOT expressed in DOT: neato (the engine
        // actually used) can't park a node in a known corner — `rank = sink` is dot-only, and a
        // pinned `pos` has no idea where the final bounding box is. The diagram renderer instead
        // moves the legend to the lower-left corner while post-processing the rendered SVG.
        dot.append("        label = <\n");
        dot.append("            <table border=\"0\" cellborder=\"1\" cellspacing=\"0\" cellpadding=\"6\" bgcolor=\"#FFFFFF\">\n");
        // Header band.
        dot.append("                <tr>\n");
        dot.append("                    <td colspan=\"3\" bgcolor=\"#E2E8F0\"><b><font point-size=\"11\" color=\"#1E293B\">Node Type Legend</font></b></td>\n");
        dot.append("                </tr>\n");
        // Node size semantics.
        dot.append("                <tr>\n");
        dot.append("                    <td bgcolor=\"#F8F9FA\" width=\"20\"></td>\n");
        dot.append("                    <td><b>Node size</b></td>\n");
        dot.append("                    <td><font color=\"#475569\">Proportional to linked-rule count (largest node = 4x base)</font></td>\n");
        dot.append("                </tr>\n");
        // Border semantics: a dashed example, a bold example and the combined case.
        dot.append("                <tr>\n");
        dot.append("                    <td bgcolor=\"#F8F9FA\" width=\"20\" style=\"dashed\"></td>\n");
        dot.append("                    <td><b>Dashed border</b></td>\n");
        dot.append("                    <td><font color=\"#475569\">Part of a synonym pair</font></td>\n");
        dot.append("                </tr>\n");
        dot.append("                <tr>\n");
        dot.append("                    <td bgcolor=\"#F8F9FA\" width=\"20\" border=\"3\"></td>\n");
        dot.append("                    <td><b>Bold border</b></td>\n");
        dot.append("                    <td><font color=\"#475569\">Approved candidate</font></td>\n");
        dot.append("                </tr>\n");
        dot.append("                <tr>\n");
        dot.append("                    <td bgcolor=\"#F8F9FA\" width=\"20\" border=\"3\" style=\"dashed\"></td>\n");
        dot.append("                    <td><b>Bold + dashed</b></td>\n");
        dot.append("                    <td><font color=\"#475569\">Approved synonym member</font></td>\n");
        dot.append("                </tr>\n");
        // Colour rows (a swatch per POS colour used by the graph).
        dot.append("                <tr>\n");
        dot.append("                    <td colspan=\"3\" bgcolor=\"#F1F5F9\"><font point-size=\"10\" color=\"#1E293B\">Node colours (fill) by part of speech</font></td>\n");
        dot.append("                </tr>\n");
        for (String[] row : COLOR_ROWS) {
            dot.append("                <tr>\n");
            dot.append("                    <td bgcolor=\"").append(row[0]).append("\" width=\"20\"></td>\n");
            dot.append("                    <td><b>").append(row[1]).append("</b></td>\n");
            dot.append("                    <td><font color=\"#475569\">").append(row[2]).append("</font></td>\n");
            dot.append("                </tr>\n");
        }
        dot.append("            </table>\n");
        dot.append("        >\n");
        dot.append("    ];\n");
    }
}
