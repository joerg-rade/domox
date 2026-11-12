package domox.dom.crc.diagram;

/**
 * Node-sizing metrics shared by every lexical diagram renderer
 * ({@link CandidateD3Graph}, {@link CandidateVegaGraph}, {@link DomainModelDiagramsD3}).
 * <p>
 * A node's radius grows linearly with the number of {@code RuleMatch}es behind its lemma, from
 * {@link #NODE_BASE_RADIUS} at {@link #MIN_MATCH_COUNT} up to {@code NODE_BASE_RADIUS * }{@link #MAX_SCALE}
 * at the largest count in the diagram.  Previously each renderer carried its own copy of the three
 * constants and the identical {@code scaleFor} formula; this collaborator is the single source of
 * truth.
 */
public final class DiagramNodeSizing {

    /** Base node radius (px), enlarged proportionally to rule-match count. */
    public static final double NODE_BASE_RADIUS = 8.0;

    /** Rule-match count that maps to scale 1 (no enlargement). */
    public static final int MIN_MATCH_COUNT = 1;

    /** Rule-match count of the largest node maps to scale {@value #MAX_SCALE}. */
    public static final double MAX_SCALE = 4.0;

    private DiagramNodeSizing() {
    }

    /**
     * Linear scale: count {@link #MIN_MATCH_COUNT} → {@code 1.0}, {@code maxCount} → {@link #MAX_SCALE}.
     * Clamped below {@code 1.0}; a {@code maxCount} at or below {@link #MIN_MATCH_COUNT} yields {@code 1.0}.
     */
    public static double scaleFor(final int count, final int maxCount) {
        if (maxCount <= MIN_MATCH_COUNT) return 1.0;
        final int n = Math.max(count, MIN_MATCH_COUNT);
        return 1.0 + (MAX_SCALE - 1.0) * (n - MIN_MATCH_COUNT) / (maxCount - MIN_MATCH_COUNT);
    }

    /** The rounded node radius (px) for the given rule-match count. */
    public static int radiusFor(final int count, final int maxCount) {
        return (int) Math.round(scaleFor(count, maxCount) * NODE_BASE_RADIUS);
    }
}
