package domox.dom.rqm;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.BookmarkPolicy;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.annotation.TableDecorator;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.util.ObjectContracts;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Corpus")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "files-o",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
public class Corpus implements Comparable<Corpus> {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
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
    private List<Document> documents;

    @PropertyLayout(sequence = "2")
    @Column(nullable = false)
    private Timestamp analyzedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Corpus other) {
        return ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return ObjectContracts.toString(this, "id");
    }
    //endregion

    //TODO reference repositoryService here or delegate to Factory ?
    public void addDocument(Document document) {
        this.documents.add(document);
//        repositoryService.persistAndFlush(obj);
//        repositoryService.persistAndFlush(document);
    }
}
