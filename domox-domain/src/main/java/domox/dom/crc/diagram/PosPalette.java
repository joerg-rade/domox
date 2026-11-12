package domox.dom.crc.diagram;

/**
 * Penn Treebank POS code → node fill colour, shared by the D3-style diagram renderers
 * ({@link CandidateD3Graph}, {@link DomainModelDiagramsD3}).
 * <p>
 * The colour must be identical everywhere so the client legend and the rendered nodes agree.
 * {@link CandidateVegaGraph} colours its nodes from the coarser {@link PosLegend} categories
 * instead and does not use this palette.
 */
public final class PosPalette {

    private PosPalette() {
    }

    /** Penn POS code → fill colour (an unknown code falls back to grey). */
    public static String color(final String pos) {
        return switch (pos) {
            case "NN", "NNS" -> "#3498DB";
            case "VB", "VBZ", "VBD", "VBG", "VBN", "VBP" -> "#E74C3C";
            case "JJ", "JJR", "JJS" -> "#2ECC71";
            case "DT" -> "#F39C12";
            case "PRP", "PRP$" -> "#9B59B6";
            case "IN" -> "#17A2B8";
            case "RB" -> "#FFBB28";
            case "CD" -> "#F1C40F";
            case "CC" -> "#00C49F";
            case "WDT", "WP", "WP$", "WRB" -> "#9B59B6";
            case "NNP", "NNPS" -> "#85C1E9";
            case ",", ".", "!", "?", ";", ":" -> "#FFFFFF";
            default -> "#AAB7B8";
        };
    }
}
