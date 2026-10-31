package domox.dom.crc.repository;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponsibilityCddRepository extends JpaRepository<ResponsibilityCdd, Long> {

    ResponsibilityCdd findByCandidateName(final String candidateName);

    ResponsibilityCdd findByCandidateNameAndDomainModel(final String candidateName, final DomainModel domainModel);
}