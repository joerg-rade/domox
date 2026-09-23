package domox.webapp.mcp;

import domox.dom.crc.Candidate;
import domox.dom.crc.Review;
import domox.dom.crc.ReviewRationale;
import domox.dom.crc.ReviewStatus;

/**
 * Result of an agent approval/rejection MCP tool call.
 *
 * @param candidateId   the id of the candidate that was reviewed
 * @param candidateName the name of the candidate that was reviewed
 * @param status        the resulting {@link ReviewStatus} (APPROVED or REJECTED)
 * @param rationale     the rationale for a rejection, otherwise {@code null}
 * @param reviewId      the id of the (new or updated) review row
 * @param reviewer      the reviewer attributed to the decision (always the agent)
 */
public record ReviewResult(
        long candidateId,
        String candidateName,
        String status,
        String rationale,
        long reviewId,
        String reviewer) {

    public static ReviewResult approved(Review review, Candidate candidate) {
        return new ReviewResult(
                candidate.getId(),
                candidate.getCandidateName(),
                String.valueOf(ReviewStatus.APPROVED),
                null,
                review.getId(),
                review.getUser());
    }

    public static ReviewResult rejected(Review review, Candidate candidate, ReviewRationale rationale) {
        return new ReviewResult(
                candidate.getId(),
                candidate.getCandidateName(),
                String.valueOf(ReviewStatus.REJECTED),
                rationale != null ? rationale.name() : null,
                review.getId(),
                review.getUser());
    }
}