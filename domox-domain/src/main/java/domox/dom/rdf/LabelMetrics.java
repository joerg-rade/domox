package domox.dom.rdf;

/**
 * The <b>label metrics</b> of the UML reconstruction: how wide a class label is at the diagram font, and
 * the spacing kept around a label.
 *
 * <p>The domain has no font to measure with, so {@link #estimatedLabelWidth(String)} adds up a
 * per-character advance table (12px bold sans-serif, measured in Chromium and padded by
 * {@link #LABEL_SAFETY}).  The client measures the real text and shrinks only when this estimate still
 * falls short — the exception, not the rule, so the whole diagram keeps one font size.</p>
 */
final class LabelMetrics {

    /** Padding kept inside a box, around its label, when widening the box to fit. */
    static final int LABEL_PADDING = 14;

    /** Kept clear between two neighbouring boxes so a widened one does not touch the next. */
    static final int LABEL_GAP = 8;

    /**
     * Safety factor on the estimated label width — the domain cannot measure the real font, so it errs
     * wide.  A narrower font than the one measured just leaves a slightly roomier box.
     */
    private static final double LABEL_SAFETY = 1.10;

    /** Advance width per lower-case letter, at the label font (12px bold sans-serif), in model units. */
    private static final double[] LOWER = {
            6.7, 7.3, 6.7, 7.3, 6.7, 4.0, 7.3, 7.3, 3.3, 3.3, 6.7, 3.3, 10.7,
            7.3, 7.3, 7.3, 7.3, 4.7, 6.7, 4.0, 7.3, 6.7, 9.3, 6.7, 6.7, 6.0};

    /** Advance width per upper-case letter, same conditions. */
    private static final double[] UPPER = {
            8.7, 8.7, 8.7, 8.7, 8.0, 7.3, 9.3, 8.7, 3.3, 6.7, 8.7, 7.3, 10.0,
            8.7, 9.3, 8.0, 9.3, 8.7, 8.0, 7.3, 8.7, 8.0, 11.3, 8.0, 8.0, 7.3};

    /**
     * Estimated width of a label, in model units, at the label font size.
     *
     * <p>Uses a per-character advance table (12px bold sans-serif, measured in Chromium and padded by
     * {@link #LABEL_SAFETY}) — the client measures the real text and shrinks only when this estimate still
     * falls short.</p>
     */
    static double estimatedLabelWidth(final String label) {
        if (label == null) {
            return 0;
        }
        double width = 0;
        for (int i = 0; i < label.length(); i++) {
            width += advance(label.charAt(i));
        }
        return width * LABEL_SAFETY;
    }

    /** Advance width of one character, in model units at the label font size. */
    private static double advance(final char c) {
        if (c >= 'a' && c <= 'z') {
            return LOWER[c - 'a'];
        }
        if (c >= 'A' && c <= 'Z') {
            return UPPER[c - 'A'];
        }
        if (c >= '0' && c <= '9') {
            return 6.7;
        }
        return switch (c) {
            case '_' -> 6.7;
            case ' ' -> 3.8;
            case '(', ')', '-' -> 4.0;
            case '.', ',' -> 3.3;
            default -> 8.0;             // an unmeasured character: assume a wide one
        };
    }
}
