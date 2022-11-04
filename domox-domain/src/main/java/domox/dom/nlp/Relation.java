package domox.dom.nlp;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.List;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@javax.persistence.EntityListeners(CausewayEntityListener.class)
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "arrows-h")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Relation implements Comparable<Relation> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(name = "id", nullable = false)
    private Long id;

    @javax.persistence.Version
    @javax.persistence.Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @javax.persistence.Column(nullable = false)
    private RelationType type;

    //TODO cardinality 1:2
    @javax.persistence.OneToMany(mappedBy = "relation")
    private List<TypedDependency> modelDependencies;

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
