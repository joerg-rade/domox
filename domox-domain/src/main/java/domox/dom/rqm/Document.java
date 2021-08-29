package domox.dom.rqm;

import domox.dom.nlp.Sentence;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.value.Clob;

import javax.persistence.CascadeType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.Set;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "Document_title_UNQ", columnNames = {"title"})
        }
)
@DomainObject(nature=Nature.ENTITY, logicalTypeName = "domox.Document", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "file")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Document implements Comparable<Document> {

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
    private String title;

    @javax.persistence.Column(nullable = false)
    @Property()
    private String docVersion; //SemVer

    @javax.persistence.Column(nullable = true)
    @Property()
    private String url;

    @javax.persistence.Column(nullable = false)
    @Property()
    private Clob content;

    @Property()
    @PropertyLayout(sequence = "5")
    @javax.persistence.OneToMany(mappedBy = "document", cascade = CascadeType.PERSIST)
    private Set<Sentence> sentences;

    @Property()
    @PropertyLayout(sequence = "6")
    @javax.persistence.ManyToMany(mappedBy = "documents", cascade = CascadeType.PERSIST)
    public Set<Author> authors;

    @javax.persistence.Column(nullable = false)
    @Property()
    private Timestamp createdAt;

    @javax.persistence.Column(nullable = true)
    @Property()
    private Timestamp updatedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Document other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "corpus_id")
    private Corpus corpus;

}
