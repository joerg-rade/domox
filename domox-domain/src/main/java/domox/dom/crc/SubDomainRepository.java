package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubDomainRepository extends JpaRepository<SubDomain, Long> {

    SubDomain findByName(final String name);
}