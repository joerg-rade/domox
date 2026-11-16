package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TypedDependencyRepository extends JpaRepository<TypedDependency, Long> {

    List<TypedDependency> findByType(final TdType type);
    List<TypedDependency> findBySentence(final Sentence sentence);

    /**
     * Every typed dependency in which {@code lemma} occurs as the governor or the dependent.
     * <p>
     * The lemmatised forms are compared case-insensitively (the query lower-cases both sides), so a
     * caller may pass either the dictionary form or the surface form as it appeared in the model
     * name.  Ordered by id so a caller that groups the results is deterministic.
     */
    @Query("SELECT td FROM TypedDependency td"
            + " WHERE LOWER(td.governorLemma) = LOWER(:lemma) OR LOWER(td.dependentLemma) = LOWER(:lemma)"
            + " ORDER BY td.id")
    List<TypedDependency> findByLemma(@Param("lemma") String lemma);

}
