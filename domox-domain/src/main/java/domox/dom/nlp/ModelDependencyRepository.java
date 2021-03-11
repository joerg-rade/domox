package domox.dom.nlp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelDependencyRepository extends JpaRepository<ModelDependency, Long> {

    List<ModelDependency> findByType(final ModelType type);

}
