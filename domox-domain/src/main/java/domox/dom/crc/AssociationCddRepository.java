package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssociationCddRepository extends JpaRepository<AssociationCdd, Long> {

    AssociationCdd findByCandidateName(final String candidateName);

    AssociationCdd findByCandidateNameAndDomainModel(final String candidateName, final DomainModel domainModel);

    java.util.List<AssociationCdd> findBySource(final ClassCdd source);

    java.util.List<AssociationCdd> findByTarget(final ClassCdd target);
}