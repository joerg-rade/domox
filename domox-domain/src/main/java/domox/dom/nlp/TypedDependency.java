package domox.dom.nlp;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.util.ObjectContracts;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.Arrays;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".TypedDependency")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "scan")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class TypedDependency implements Comparable<TypedDependency> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
    private Token partA;

    @OneToOne()
    @JoinColumn(name = "partBId")
    @Property()
    @Getter
    @Setter
    private Token partB;

    @ManyToOne()
    @JoinColumn(name = "relation_id")
    private Relation relation;

    //region > compareTo, toString
    @Override
    public int compareTo(final TypedDependency other) {
        return ObjectContracts.compare(this, other, "id");
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

    private static final PartOfSpeechType[] VERB_TYPES = {
            PartOfSpeechType.VB,
            PartOfSpeechType.VBG,
            PartOfSpeechType.VBN,
            PartOfSpeechType.VBP,
            PartOfSpeechType.VBZ};

    public boolean isVerbA() {
        final PartOfSpeechType aType = getPartA().getType();
        return Arrays.asList(VERB_TYPES).contains(aType);
    }

    private static final PartOfSpeechType[] NOUN_TYPES = {
            PartOfSpeechType.NN,
            PartOfSpeechType.NNP,
            PartOfSpeechType.NNS,
            PartOfSpeechType.NFP};

    public boolean isNounB() {
        final PartOfSpeechType bType = getPartB().getType();
        return Arrays.asList(NOUN_TYPES).contains(bType);
    }

    private static final String[] BASIC_ATTRIB = {"name", "number", "type", "address", "level", "date", "time"};

    public boolean isBasicAttributeB() {
        final Token b = getPartB();
        final String name = b.toString();
        for (String s : BASIC_ATTRIB) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
