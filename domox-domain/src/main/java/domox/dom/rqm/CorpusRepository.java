package domox.dom.rqm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorpusRepository extends JpaRepository<Corpus, Long> {

    List<Corpus> findByTitleContaining(final String title);

    Corpus findByTitle(final String title);

}
