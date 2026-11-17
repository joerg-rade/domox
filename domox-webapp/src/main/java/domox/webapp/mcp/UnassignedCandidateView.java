package domox.webapp.mcp;

/**
 * A class candidate that has no sub-domain yet, without its rule-match evidence — the list a
 * sub-domain pass starts from, kept lean because it can run to hundreds of rows.
 *
 * <p>Call {@code subDomainEvidenceForCandidate} for one candidate's full evidence.</p>
 *
 * @param id            the candidate id
 * @param candidateName the candidate name
 * @param candidateType the candidate type (e.g. {@code ClassCdd})
 * @param approvalCount how many reviews approved it; only approved candidates should be assigned
 */
public record UnassignedCandidateView(
        long id,
        String candidateName,
        String candidateType,
        int approvalCount) {
}
