package domox.dom.rqm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    List<Author> findByNameContaining(final String name);

    Author findByName(final String name);

}
