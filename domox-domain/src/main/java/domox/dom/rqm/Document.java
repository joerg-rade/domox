package domox.dom.rqm;

import domox.dom.nlp.Sentence;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.value.Clob;

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
@DomainObject(nature = Nature.ENTITY, logicalTypeName = "domox.Document", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "file")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
//@Data
public class Document implements Comparable<Document> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    private Long id;

    @javax.persistence.Version
    @javax.persistence.Column(nullable = false)
    private int version;

    @PropertyLayout(sequence = "1")
    @javax.persistence.Column(nullable = false)
    @Getter
    @Setter
    private String title;

    @PropertyLayout(sequence = "2")
    @javax.persistence.Column(nullable = false)
    @Getter
    @Setter
    private String docVersion; //SemVer

    @PropertyLayout(sequence = "3")
    @javax.persistence.Column(nullable = true)
    @Getter
    @Setter
    private String url;

    @PropertyLayout(sequence = "4")
    @javax.persistence.Column(nullable = false)
    @Getter
    @Setter
    private Clob content;

    @PropertyLayout(sequence = "5")
    @javax.persistence.OneToMany(mappedBy = "document", cascade = CascadeType.PERSIST)
    @Getter
    @Setter
    private Set<Sentence> sentences;

    @PropertyLayout(sequence = "6")
    @javax.persistence.ManyToMany(mappedBy = "documents", cascade = CascadeType.PERSIST)
    @Getter
    @Setter
    public Set<Author> authors;

    @javax.persistence.Column(nullable = false)
    @Getter
    @Setter
    private Timestamp createdAt;

    @javax.persistence.Column(nullable = true)
    private Timestamp updatedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Document other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.causeway.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "corpus_id")
    private Corpus corpus;

}
