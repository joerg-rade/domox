package domox.webapp.mcp;

import java.util.Arrays;

import jakarta.inject.Inject;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import domox.dom.crc.Candidate;
import domox.dom.crc.Reviews;
import domox.dom.crc.ReviewRationale;

/**
 * Exposes the automated Candidate-review workflow to LLM clients over MCP
 * (Model Context Protocol).
 * <p>
 * These tools let an agent/LLM replace the human-in-the-loop reviewer: it fetches the
 * next un-processed candidate together with its rule-match evidence, then approves or
 * rejects it. Decided reviews are attributed to the {@link Reviews#AGENT_USER} reviewer.
 * <p>
 * Methods annotated with {@link McpTool} are auto-scanned and exposed as tools by the
 * {@code spring-ai-starter-mcp-server-webmvc} server on the {@code /mcp} endpoint
 * (zero registration code - see {@code McpServerAnnotationScannerAutoConfiguration}).
 * <p>
 * The MCP annotation scanner invokes the annotated methods reflectively on the raw bean
 * (bypassing the AOP proxy), so a transaction is opened explicitly with a
 * {@link TransactionTemplate} instead of {@code @Transactional} to keep every tool call
 * transactional (and to join an existing one when invoked from a test/service flow).
 */
@Component
public class ReviewMcpTools {

    private final Reviews reviews;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public ReviewMcpTools(Reviews reviews, PlatformTransactionManager transactionManager) {
        this.reviews = reviews;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @McpTool(
            name = "nextCandidateForReview",
            description = """
                    Returns the next class-diagram candidate that still needs to be reviewed,
                    together with its rule-match evidence (including the quoted source sentence text),
                    or a message stating that none remain. Call this first to fetch a candidate,
                    then decide whether to approve or reject it.""")
    public NextCandidateResult nextCandidateForReview() {
        return transactionTemplate.execute(status -> NextCandidateResult.of(reviews.nextUnprocessed()));
    }

    @McpTool(
            name = "approveCandidate",
            description = """
                    Approve the candidate with the given id as a valid class-diagram element.
                    The decision is recorded under the 'agent' reviewer. Safe to call repeatedly (idempotent).""")
    public ReviewResult approveCandidate(
            @McpToolParam(description = "The numeric id of the candidate to approve (as returned by nextCandidateForReview).") long candidateId) {
        return transactionTemplate.execute(status -> {
            final Candidate candidate = requireCandidate(candidateId);
            return ReviewResult.approved(reviews.approveAsAgent(candidate), candidate);
        });
    }

    @McpTool(
            name = "rejectCandidate",
            description = """
                    Reject the candidate with the given id as not valid for inclusion in the class
                    diagram, giving a rationale. The decision is recorded under the 'agent' reviewer.
                    Safe to call repeatedly (idempotent).""")
    public ReviewResult rejectCandidate(
            @McpToolParam(description = "The numeric id of the candidate to reject.") long candidateId,
            @McpToolParam(description = "Why the candidate is being rejected. One of: DUPLICATE, WRONG_CANDIDATE_TYPE, NOT_RELEVANT, INSUFFICIENT_INFORMATION, OTHER.") String rationale) {
        return transactionTemplate.execute(status -> {
            final Candidate candidate = requireCandidate(candidateId);
            final ReviewRationale parsed = parseRationale(rationale);
            return ReviewResult.rejected(reviews.rejectAsAgent(candidate, parsed), candidate, parsed);
        });
    }

    private Candidate requireCandidate(long candidateId) {
        final Candidate candidate = reviews.findByUniqueId(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("No candidate found with id " + candidateId);
        }
        return candidate;
    }

    private static ReviewRationale parseRationale(String rationale) {
        try {
            return ReviewRationale.valueOf(rationale);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unknown rationale '" + rationale + "'. Must be one of " + Arrays.toString(ReviewRationale.values()), e);
        }
    }
}
