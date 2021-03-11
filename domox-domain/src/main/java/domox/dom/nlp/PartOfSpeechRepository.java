package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartOfSpeechRepository extends JpaRepository<PartOfSpeech, Long> {

    List<PartOfSpeech> findByType(final PosType type);

}
