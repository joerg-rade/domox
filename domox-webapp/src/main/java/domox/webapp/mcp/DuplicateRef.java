package domox.webapp.mcp;

import domox.dom.crc.model.Candidate;

/**
 * A candidate that already carries the same name as the one under review <em>and is approved</em> —
 * the duplicate signal for the automated reviewer.
 *
 * @param candidateId   the colliding candidate's id
 * @param candidateName its name (as stored)
 * @param candidateType its candidate type, e.g. {@code ClassCdd} / {@code ActionCdd}
 */
public record DuplicateRef(long candidateId, String candidateName, String candidateType) {

    public static DuplicateRef from(final Candidate candidate) {
        return new DuplicateRef(
                candidate.getId(),
                candidate.getCandidateName(),
                candidate.getCandidateType());
    }
}
