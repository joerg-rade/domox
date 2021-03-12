package domox.dom.nlp;

import domox.dom.rqm.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.value.Clob;
import org.apache.isis.persistence.jpa.applib.integration.JpaEntityInjectionPointResolver;

import javax.persistence.FetchType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Set;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "Sentence_raw_UNQ", columnNames = {"raw"})
        }
)
@javax.persistence.EntityListeners(JpaEntityInjectionPointResolver.class) // injection support
@DomainObject(objectType = "domox.Sentence", editing = Editing.DISABLED)
@DomainObjectLayout()
@NoArgsConstructor
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Sentence implements Comparable<Sentence> {

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
    private String raw;

    @javax.persistence.Column(nullable = true)
    @Property()
    private Clob parsed;

    @javax.persistence.OneToOne()
    @javax.persistence.JoinColumn(name = "documentId")
    @Property()
    private Document document;

    @javax.persistence.OneToMany(mappedBy = "sentence")
    private Set<PartOfSpeech> partsOfSpeech;

    //region > compareTo, toString
    @Override
    public int compareTo(final Sentence other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
