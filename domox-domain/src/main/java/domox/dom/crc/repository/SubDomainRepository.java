package domox.dom.crc.repository;
import domox.dom.crc.model.SubDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubDomainRepository extends JpaRepository<SubDomain, Long> {

    SubDomain findByName(final String name);

    /**
     * Every sub-domain whose name matches case-insensitively, lowest id first.
     * <p>
     * A {@code List} rather than a single result on purpose: nothing constrains the name to be
     * unique, so a duplicate (two names differing only in case, or a row created before the
     * case-insensitive look-up existed) must not become a {@code NonUniqueResultException} — the
     * caller takes the first and moves on.  This is what {@code SubDomains#findOrCreate} uses, so
     * that re-running an assignment pass with {@code "Checkout"} does not create a second
     * {@code "checkout"} sub-domain.
     */
    List<SubDomain> findAllByNameIgnoreCaseOrderByIdAsc(final String name);
}