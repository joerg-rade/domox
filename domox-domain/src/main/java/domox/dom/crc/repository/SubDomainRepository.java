package domox.dom.crc.repository;
import domox.dom.crc.model.SubDomain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubDomainRepository extends JpaRepository<SubDomain, Long> {

    SubDomain findByName(final String name);
}