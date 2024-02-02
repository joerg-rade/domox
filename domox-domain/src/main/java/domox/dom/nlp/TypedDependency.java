package domox.dom.nlp;

import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.TypedDependency")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "scan")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class TypedDependency implements Comparable<TypedDependency> {

    @Id
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    @Property()
    @Getter
    @Setter
    private TdType type;

    @OneToOne()
    @JoinColumn(name = "partAId")
    @Property()
    @Getter
    @Setter
    private Word partA;

    @OneToOne()
    @JoinColumn(name = "partBId")
    @Property()
    @Getter
    @Setter
    private Word partB;

    @ManyToOne()
    @JoinColumn(name = "relation_id")
    private Relation relation;

    //region > compareTo, toString
    @Override
    public int compareTo(final TypedDependency other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

    public boolean nsubj() {
        return getType().equals(TdType.NSUBJ);
    }

    public boolean nsubjpass() {
        return getType().equals(TdType.NSUBJ);
    }

    public boolean compound() {
        return getType().equals(TdType.COMPOUND);
    }

    public boolean isVerbA() {
        final Word a = getPartA();
        return a.getType().equals(PosType.VERB);
    }

    public boolean isNounB() {
        final Word b = getPartB();
        return b.getType().equals(PosType.NOUN_PHRASE);
    }

    private static final String[] BASIC_ATTRIB = {"name", "number", "type", "address", "level", "date", "time"};

    public boolean isBasicAttributeB() {
        final Word b = getPartB();
        final String name = b.toString();
        for (String s : BASIC_ATTRIB) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
