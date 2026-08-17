package domox.dom.uml;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassCddRepository extends JpaRepository<ClassCdd, Long> {

    ClassCdd findByName(final String candidateName);
}
