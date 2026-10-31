package domox.dom.crc.repository;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaboratorCddRepository extends JpaRepository<CollaboratorCdd, Long> {

    CollaboratorCdd findByCandidateName(final String candidateName);

    CollaboratorCdd findByCandidateNameAndDomainModel(final String candidateName, final DomainModel domainModel);

    java.util.List<CollaboratorCdd> findBySource(final ClassCdd source);

    java.util.List<CollaboratorCdd> findByTarget(final ClassCdd target);
}