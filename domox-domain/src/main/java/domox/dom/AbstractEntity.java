package domox.dom;

import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

/**
 * Abstract base class for all domain entities.
 * <p>
 * Provides a common {@code id} (primary key) and {@code version} (optimistic locking)
 * column, along with shared JPA and JAXB annotations that are inherited by
 * concrete subclasses.
 * <p>
 * Concrete entities must still declare {@code @Entity}, {@code @Table},
 * {@code @Named}, {@code @DomainObject}, etc. as needed.
 */
@MappedSuperclass
@EntityListeners(CausewayEntityListener.class)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    @Getter
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

}