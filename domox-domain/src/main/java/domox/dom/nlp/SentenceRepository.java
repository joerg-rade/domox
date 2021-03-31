package domox.dom.nlp;

import domox.dom.rqm.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SentenceRepository extends JpaRepository<Sentence, Long> {

    List<Sentence> findByDocument(final Document document);

}
