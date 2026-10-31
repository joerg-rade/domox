package domox.dom.crc.repository;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeCddRepository extends JpaRepository<AttributeCdd, Long> {
    AttributeCdd findByClassCddAndCandidateName(ClassCdd classCdd, String candidateName);
}
