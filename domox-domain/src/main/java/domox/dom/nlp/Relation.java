package domox.dom.nlp;

import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.Relation")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "paragraph")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
//@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Relation implements Comparable<Relation> {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Column(nullable = false)
    private RelationType type;

    //TODO cardinality 1:2
    @OneToMany(mappedBy = "relation")
    private List<TypedDependency> modelDependencies = new ArrayList<>();

    //region > compareTo, toString
    @Override
    public int compareTo(final Relation other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.causeway.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
