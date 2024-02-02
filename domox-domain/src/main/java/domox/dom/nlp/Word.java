package domox.dom.nlp;

import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;

import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@Entity
@Table(
        schema = "domox",
        uniqueConstraints = {
                @UniqueConstraint(name = "PartOfSpeech_id_UNQ", columnNames = {"id"})
        }
)
@Named("domox.Word")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "speech")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Word implements Comparable<Word> {

    @Id
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column()
    private int version;

    @Column(nullable = false)
    @Property()
    private String text;

    @Column(nullable = false)
    @Property()
    @Getter
    @Setter
    private PosType type;

    @ManyToOne()
    @JoinColumn(name = "sentence_id")
    private Sentence sentence;

    //region > compareTo, toString
    @Override
    public int compareTo(final Word other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
