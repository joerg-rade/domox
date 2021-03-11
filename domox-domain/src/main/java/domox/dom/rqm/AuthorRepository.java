package domox.dom.rqm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    List<Author> findByLastNameContaining(final String name);

    Author findByLastName(final String name);

}
