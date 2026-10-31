package domox.dom.crc.repository;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassCddRepository extends JpaRepository<ClassCdd, Long> {

    ClassCdd findByCandidateName(final String candidateName);

    ClassCdd findByCandidateNameAndDomainModel(final String candidateName, final DomainModel domainModel);
}
