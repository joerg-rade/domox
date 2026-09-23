package domox.webapp.mcp;

import domox.dom.crc.Candidate;
import domox.dom.rules.RuleMatch;

import java.util.Comparator;
import java.util.List;

/**
 * A serializable, evidence-focused view of a {@link Candidate} for an LLM reviewer.
 * Exposes the class-diagram fact being proposed and the rule-match evidence (including
 * source sentence text) so the model can approve or reject it.
 *
 * @param id             the candidate id
 * @param candidateName  the proposed name
 * @param candidateType  the proposed type (e.g. Class, Property, Action)
 * @param className      the concrete candidate class (e.g. ClassCdd)
 * @param ruleMatchCount how many rules matched
 * @param approvalCount  how many prior reviews approved it
 * @param evidence       the rule-match evidence, ordered by id
 */
public record CandidateView(
        long id,
        String candidateName,
        String candidateType,
        String className,
        int ruleMatchCount,
        int approvalCount,
        List<RuleMatchEvidence> evidence) {

    public static CandidateView from(Candidate candidate) {
        final List<RuleMatchEvidence> evidence = candidate.getRuleMatches().stream()
                .sorted(Comparator.comparingLong(RuleMatch::getId))
                .map(RuleMatchEvidence::from)
                .toList();
        return new CandidateView(
                candidate.getId(),
                candidate.getCandidateName(),
                candidate.getCandidateType(),
                candidate.getClass().getSimpleName(),
                candidate.getRuleMatchCount(),
                candidate.getApprovalCount(),
                evidence);
    }
}