package domox.webapp.mcp;

import domox.dom.crc.Candidate;

/**
 * Result of the {@code nextCandidateForReview} MCP tool: either the next candidate to
 * review (with evidence) or a message explaining that none remain.
 *
 * @param candidate the next candidate to review, or {@code null} when the queue is empty
 * @param message   an explanatory message (set only when there is no candidate)
 */
public record NextCandidateResult(CandidateView candidate, String message) {

    public static final String NO_CANDIDATES_MESSAGE = "No candidates are left to review.";

    public static NextCandidateResult of(Candidate candidate) {
        if (candidate == null) {
            return new NextCandidateResult(null, NO_CANDIDATES_MESSAGE);
        }
        return new NextCandidateResult(CandidateView.from(candidate), null);
    }
}