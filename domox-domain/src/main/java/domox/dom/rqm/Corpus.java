package domox.dom.rqm;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.Set;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox"
)
@DomainObject(logicalTypeName = "domox.Corpus", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "files-o")
@NoArgsConstructor
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Corpus implements Comparable<Corpus> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    @Programmatic
    private Long id;

    @javax.persistence.Version
    @Programmatic
    @javax.persistence.Column(nullable = false)
    private int version;

    @javax.persistence.Column(nullable = true)
    @Property()
    private String title;

    @Property()
    @javax.persistence.OneToMany(mappedBy = "corpus")
    private Set<Document> documents;

    @javax.persistence.Column(nullable = false)
    @Property()
    private Timestamp analyzedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Corpus other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

    //TODO reference repositoryService here or delegate to Factory ?
    public void addDocument(Document document) {
        this.getDocuments().add(document);
//        repositoryService.persistAndFlush(obj);
//        repositoryService.persistAndFlush(document);
    }
}
