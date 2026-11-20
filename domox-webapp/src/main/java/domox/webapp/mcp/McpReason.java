package domox.webapp.mcp;

import domox.dom.crc.model.RationaleText;

/**
 * The one guard shared by every MCP <em>write</em> tool: <b>whenever something is changed through
 * the MCP API, the client must say why.</b>
 * <p>
 * A reason is not decoration — it is what makes an automated decision reviewable later.  The
 * review pass already records a categorical rationale ({@code Review.rationale}: DUPLICATE,
 * NOT_RELEVANT, …); this is the free text behind it, stored on the object that changed
 * ({@code Candidate.rationale}, {@code SubDomain.rationale}) so the justification can be read back
 * from the object itself rather than only from a client transcript.
 * <p>
 * Deliberately strict — a blank reason is refused rather than defaulted, because a silently
 * recorded "no reason given" is exactly the state this exists to prevent.  Over-long text is not
 * refused: it is normalised (truncated) by {@link RationaleText}, so a verbose agent degrades
 * instead of aborting its own transaction.
 */
final class McpReason {

    private McpReason() {
    }

    /**
     * Returns {@code reason} trimmed, or throws when the client did not give one.
     *
     * @throws IllegalArgumentException when {@code reason} is {@code null} or blank
     */
    static String require(final String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required: state in 'reason' why this change is being made. "
                    + "It is recorded on the object that changes.");
        }
        return RationaleText.normalise(reason);
    }
}
