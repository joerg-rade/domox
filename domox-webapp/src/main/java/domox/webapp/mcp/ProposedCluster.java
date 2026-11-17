package domox.webapp.mcp;

import java.util.List;

/**
 * One proposed sub-domain of a {@link SubDomainPartitionSuggestion}.
 *
 * @param proposedName   a name derived from the use-case titles; rename it if the business calls the
 *                       area something else
 * @param classification the proposed classification ({@code GENERIC} only when the cluster is
 *                       demonstrably broad, otherwise {@code UNCLASSIFIED})
 * @param candidateIds   the candidates this cluster owns
 * @param candidateNames the same candidates' names, so the proposal can be read without a second call
 * @param reasons        the evidence behind the cluster, in the order it was weighed
 * @param confidence     {@code HIGH}, {@code MEDIUM} or {@code LOW}
 */
public record ProposedCluster(
        String proposedName,
        String classification,
        List<Long> candidateIds,
        List<String> candidateNames,
        List<String> reasons,
        String confidence) {

    public ProposedCluster {
        candidateIds = List.copyOf(candidateIds == null ? List.of() : candidateIds);
        candidateNames = List.copyOf(candidateNames == null ? List.of() : candidateNames);
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
}
