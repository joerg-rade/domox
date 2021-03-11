package domox.dom.rqm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByTitleContaining(final String title);

    Document findByTitle(final String title);

}
