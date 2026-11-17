package domox.webapp.mcp;

import domox.dom.crc.service.SubDomainAdvisor;

import java.util.List;
import java.util.Map;

/**
 * An <b>advisory</b> sub-domain partition over the unassigned, approved class candidates.
 *
 * <p>Produced by {@code SubDomainAdvisor} from the use-case provenance of each candidate.  It is
 * advice, not a decision: weigh it against the business, rename the clusters to the words the
 * organisation actually uses, decide core / supporting / generic yourself, and only then create the
 * sub-domains and assign.</p>
 *
 * @param clusters                 the proposed sub-domains, largest first
 * @param crossCuttingCandidateIds candidates whose evidence spans many use cases — shared concepts,
 *                                 deliberately left out of every cluster
 * @param unevidencedCandidateIds  candidates with no rule-match evidence at all
 * @param notes                    observations that did not fit a single cluster
 */
public record SubDomainPartitionSuggestion(
        List<ProposedCluster> clusters,
        List<Long> crossCuttingCandidateIds,
        List<Long> unevidencedCandidateIds,
        List<String> notes) {

    public SubDomainPartitionSuggestion {
        clusters = List.copyOf(clusters == null ? List.of() : clusters);
        crossCuttingCandidateIds = List.copyOf(crossCuttingCandidateIds == null ? List.of() : crossCuttingCandidateIds);
        unevidencedCandidateIds = List.copyOf(unevidencedCandidateIds == null ? List.of() : unevidencedCandidateIds);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    /**
     * Maps the advisor's proposal onto the serializable view, resolving candidate ids to names so the
     * proposal can be read (and decided on) without a second call per cluster.
     */
    public static SubDomainPartitionSuggestion of(
            final SubDomainAdvisor.Proposal proposal,
            final Map<Long, String> candidateNamesById) {
        final List<ProposedCluster> clusters = proposal.clusters().stream()
                .map(cluster -> new ProposedCluster(
                        cluster.proposedName(),
                        cluster.classification() != null ? cluster.classification().name() : null,
                        cluster.candidateIds(),
                        cluster.candidateIds().stream()
                                .map(id -> candidateNamesById.getOrDefault(id, "?"))
                                .toList(),
                        cluster.reasons(),
                        cluster.confidence() != null ? cluster.confidence().name() : null))
                .toList();
        return new SubDomainPartitionSuggestion(
                clusters,
                proposal.crossCuttingCandidateIds(),
                proposal.unevidencedCandidateIds(),
                proposal.notes());
    }
}
