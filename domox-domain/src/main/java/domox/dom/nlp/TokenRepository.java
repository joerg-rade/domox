package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenRepository extends JpaRepository<Token, Long> {

    List<Token> findByType(final PartOfSpeechType type);

}
