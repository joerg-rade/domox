package domox.dom.crc.model;

/**
 * The single rule for storing the free-text <em>reason</em> a client gives for a change —
 * {@link Candidate#rationale} and {@link SubDomain#rationale}.
 * <p>
 * Two entities record a "why" and they must agree on its length and on what an empty one means,
 * so the rule lives here rather than being copy-pasted into each setter.  The MCP write tools
 * ({@code McpReason} on the webapp side) <em>require</em> the text to be non-blank; this class
 * only normalises whatever it is handed.
 * <p>
 * Postgres treats {@code varchar(n)} as a length <em>constraint</em> only, so 2000 characters
 * costs no more than a short column and a longer value is TOASTed transparently — the cap bounds
 * what a client may store, it is not a performance knob.
 */
public final class RationaleText {

    /** The stored length limit, shared by the JPA column lengths of every {@code rationale} field. */
    public static final int MAX_LENGTH = 2000;

    private RationaleText() {
    }

    /**
     * Returns {@code raw} trimmed, with blank mapped to {@code null} and anything longer than
     * {@link #MAX_LENGTH} truncated rather than rejected — a long rationale degrades instead of
     * aborting the caller's transaction.
     */
    public static String normalise(final String raw) {
        final String trimmed = raw == null ? null : raw.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
