package domox.webapp.mcp;

import java.util.List;

/**
 * Result of the {@code suggestReview} MCP tool.
 *
 * <p><b>Advice, not a decision.</b>  The suggestion is derived from ontology evidence and duplicate
 * detection only; it does not see the sentence evidence or the review policy.  An agent must weigh it
 * against the rule-match evidence and remain free to decide the opposite.</p>
 *
 * @param candidateId   the candidate's id
 * @param candidateName the candidate's name
 * @param suggestion    {@code APPROVE}, {@code REJECT} or {@code UNCERTAIN}
 * @param rationale     the suggested rejection rationale, {@code null} unless {@code suggestion} is
 *                      {@code REJECT}
 * @param confidence    {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param reasons       the evidence behind the suggestion, in the order it was weighed
 */
public record ReviewSuggestion(
        long candidateId,
        String candidateName,
        String suggestion,
        String rationale,
        String confidence,
        List<String> reasons) {

    /** Repeated in the tool description and here so no client can mistake the suggestion for a decision. */
    public static final String ADVISORY_NOTE =
            "Advisory only: weigh this against the rule-match evidence and decide yourself.";

    public ReviewSuggestion {
        reasons = List.copyOf(reasons);
    }
}
