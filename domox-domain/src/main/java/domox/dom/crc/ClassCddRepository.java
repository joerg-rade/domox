package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassCddRepository extends JpaRepository<ClassCdd, Long> {

    ClassCdd findByCandidateName(final String candidateName);
}
