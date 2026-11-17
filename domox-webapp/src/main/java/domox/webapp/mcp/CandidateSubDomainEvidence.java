package domox.webapp.mcp;

import domox.dom.requirements.UseCaseProvenance;

import java.util.List;

/**
 * The use-case evidence behind one class candidate — what the sub-domain decision for that candidate
 * is made on.
 *
 * <p>{@link UseCaseProvenance} is reused rather than re-mapped: it is already a plain record of
 * primitives and strings, with no entity references to leak.</p>
 *
 * @param candidateId     the candidate id
 * @param candidateName   the candidate name
 * @param candidateType   the candidate type
 * @param approved        whether at least one review approved it
 * @param currentSubDomain the sub-domain it is assigned to, or {@code null}
 * @param useCases        the use cases its rule-match evidence comes from, most-evidenced first
 * @param notes           observations about this candidate's evidence
 */
public record CandidateSubDomainEvidence(
        long candidateId,
        String candidateName,
        String candidateType,
        boolean approved,
        String currentSubDomain,
        List<UseCaseProvenance> useCases,
        List<String> notes) {

    public CandidateSubDomainEvidence {
        useCases = List.copyOf(useCases == null ? List.of() : useCases);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}
