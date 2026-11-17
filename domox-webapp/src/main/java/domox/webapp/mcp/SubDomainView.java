package domox.webapp.mcp;

import domox.dom.crc.model.SubDomain;

/**
 * A sub-domain and how much of the class model it currently owns.
 *
 * @param id                     the sub-domain id
 * @param name                   the sub-domain name
 * @param classification         {@code CORE}, {@code SUPPORTING}, {@code GENERIC} or {@code UNCLASSIFIED}
 * @param assignedCandidateCount how many class candidates are assigned to it
 */
public record SubDomainView(
        long id,
        String name,
        String classification,
        long assignedCandidateCount) {

    public static SubDomainView of(final SubDomain subDomain, final long assignedCandidateCount) {
        return new SubDomainView(
                subDomain.getId() != null ? subDomain.getId() : -1L,
                subDomain.getName(),
                subDomain.getClassification() != null ? subDomain.getClassification().name() : null,
                assignedCandidateCount);
    }
}
