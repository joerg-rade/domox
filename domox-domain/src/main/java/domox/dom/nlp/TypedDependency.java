package domox.dom.nlp;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@DomainObject(nature=Nature.ENTITY, logicalTypeName = "domox.ModelDependency", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "scan")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class TypedDependency implements Comparable<TypedDependency> {

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
    private TdType type;

    @javax.persistence.OneToOne()
    @javax.persistence.JoinColumn(name = "partAId")
    @Property()
    private Word partA;

    @javax.persistence.OneToOne()
    @javax.persistence.JoinColumn(name = "partBId")
    @Property()
    private Word partB;

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "relation_id")
    private Relation relation;

    //region > compareTo, toString
    @Override
    public int compareTo(final TypedDependency other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
