package domox.dom.nlp;

import lombok.Data;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.persistence.jpa.applib.integration.JpaEntityInjectionPointResolver;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@javax.persistence.EntityListeners(JpaEntityInjectionPointResolver.class) // injection support
@DomainObject(objectType = "domox.ModelDependency", nature = Nature.ENTITY)
@DomainObjectLayout(cssClassFa = "scan")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class ModelDependency implements  Comparable<domox.dom.nlp.ModelDependency> {

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
    private ModelType type;

    @javax.persistence.OneToOne()
    @javax.persistence.JoinColumn(name = "partAId")
    @Property()
    private PartOfSpeech partA;

    @javax.persistence.OneToOne()
    @javax.persistence.JoinColumn(name = "partBId")
    @Property()
    private PartOfSpeech partB;

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "relation_id")
    private Relation relation;

    //region > compareTo, toString
    @Override
    public int compareTo(final ModelDependency other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
