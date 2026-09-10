package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyCddRepository extends JpaRepository<PropertyCdd, Long> {
    PropertyCdd findByClassCddAndCandidateName(ClassCdd classCdd, String candidateName);
}
