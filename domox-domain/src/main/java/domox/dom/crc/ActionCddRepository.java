package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionCddRepository extends JpaRepository<ActionCdd, Long> {

    ActionCdd findByCandidateName(final String candidateName);
}