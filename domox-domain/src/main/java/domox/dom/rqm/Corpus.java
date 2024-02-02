package domox.dom.rqm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.Set;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.Corpus")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "files-o")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Corpus implements Comparable<Corpus> {

    @Id
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = true)
    @Getter
    @Setter
    private String title;

    @PropertyLayout(sequence = "1")
    @OneToMany(mappedBy = "corpus")
    private Set<Document> documents;

    @PropertyLayout(sequence = "2")
    @Column(nullable = false)
    private Timestamp analyzedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Corpus other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.causeway.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

    //TODO reference repositoryService here or delegate to Factory ?
    public void addDocument(Document document) {
        this.documents.add(document);
//        repositoryService.persistAndFlush(obj);
//        repositoryService.persistAndFlush(document);
    }
}
