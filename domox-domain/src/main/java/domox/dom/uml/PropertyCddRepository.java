package domox.dom.uml;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyCddRepository extends JpaRepository<PropertyCdd, Long> {
    PropertyCdd findByClassCddAndName(ClassCdd classCdd, String name);
}
