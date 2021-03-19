package domox.dom.nlp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.persistence.jpa.applib.integration.JpaEntityInjectionPointResolver;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "PartOfSpeech_id_UNQ", columnNames = {"id"})
        }
)
@javax.persistence.EntityListeners(JpaEntityInjectionPointResolver.class) // injection support
@DomainObject(objectType = "domox.PartOfSpeech", nature = Nature.ENTITY)
@DomainObjectLayout(cssClassFa = "speech")
@NoArgsConstructor
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class PartOfSpeech implements Comparable<PartOfSpeech> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    @Programmatic
    private Long id;

    @javax.persistence.Version
    @Programmatic
    @javax.persistence.Column()
    private int version;

    @javax.persistence.Column(nullable = false)
    @Property()
    private String content;

    @javax.persistence.Column(nullable = false)
    @Property()
    private PosType type;

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "sentence_id")
    private Sentence sentence;

    //region > compareTo, toString
    @Override
    public int compareTo(final PartOfSpeech other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
