package domox.webapp.mcp;

import java.util.List;

/**
 * Result of a sub-domain assignment MCP tool call.
 *
 * @param subDomain            the sub-domain the call targeted (or cleared, when {@code null})
 * @param classification       its classification after the call
 * @param assignedCandidateIds the candidates that were assigned
 * @param notFoundCandidateIds the requested ids that match no class candidate
 * @param messages             anything the caller should know (a created sub-domain, an
 *                             already-assigned candidate, a non-class candidate, …)
 */
public record AssignmentResult(
        String subDomain,
        String classification,
        List<Long> assignedCandidateIds,
        List<Long> notFoundCandidateIds,
        List<String> messages) {

    public AssignmentResult {
        assignedCandidateIds = List.copyOf(assignedCandidateIds == null ? List.of() : assignedCandidateIds);
        notFoundCandidateIds = List.copyOf(notFoundCandidateIds == null ? List.of() : notFoundCandidateIds);
        messages = List.copyOf(messages == null ? List.of() : messages);
    }
}
