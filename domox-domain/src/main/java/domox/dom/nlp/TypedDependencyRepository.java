package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypedDependencyRepository extends JpaRepository<TypedDependency, Long> {

    List<TypedDependency> findByType(final TdType type);

}
