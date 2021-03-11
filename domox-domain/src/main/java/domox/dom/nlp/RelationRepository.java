package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RelationRepository extends JpaRepository<Relation, Long> {

    List<Relation> findByType(final RelationType type);

}
