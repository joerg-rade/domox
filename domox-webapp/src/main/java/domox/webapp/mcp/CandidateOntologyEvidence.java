package domox.webapp.mcp;

import java.util.List;

/**
 * Result of the {@code ontologyEvidenceForCandidate} MCP tool: everything the ontology and the
 * existing decisions say about one candidate.
 *
 * @param candidateId   the candidate's id
 * @param candidateName the proposed name that was looked up
 * @param candidateType the candidate type, e.g. {@code ClassCdd}
 * @param source        what was queried (the SPARQL endpoint, or the local ontology resource)
 * @param typeAgreement {@code AGREES} / {@code MISMATCH} / {@code UNKNOWN} — whether the ontology's
 *                      kind for the term matches what the candidate type implies
 * @param matches       the ontology terms matching the candidate's name
 * @param duplicates    already-approved candidates sharing the name (empty when none)
 * @param message       why there are no matches, or why the source could not be queried
 */
public record CandidateOntologyEvidence(
        long candidateId,
        String candidateName,
        String candidateType,
        String source,
        String typeAgreement,
        List<OntologyTermView> matches,
        List<DuplicateRef> duplicates,
        String message) {
}
