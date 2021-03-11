package domox.dom.nlp;

import lombok.Data;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.persistence.jpa.applib.integration.JpaEntityInjectionPointResolver;

import javax.persistence.FetchType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.List;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@javax.persistence.EntityListeners(JpaEntityInjectionPointResolver.class) // injection support
@DomainObject(objectType = "domox.Relation", editing = Editing.DISABLED)
@DomainObjectLayout()
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Relation implements Comparable<domox.dom.nlp.Relation> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    @Programmatic
    private Long id;

    @javax.persistence.Version
    @Programmatic
    @javax.persistence.Column(nullable = false)
    private int version;

    @javax.persistence.Column(nullable = false)
    @Property()
    private RelationType type;

    @javax.persistence.JoinColumn(name = "id")
    //TODO cardinality 1:2
    @Property()
    @javax.persistence.OneToMany(mappedBy = "relation")
    private List<ModelDependency> modelDependencies;

    //region > compareTo, toString
    @Override
    public int compareTo(final Relation other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
