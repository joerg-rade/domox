package domox.dom.rqm;

import domox.DomainModule;
import domox.dom.nlp.Sentence;
import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParseException;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.BookmarkPolicy;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.Domain;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.TableDecorator;
import org.apache.causeway.applib.annotation.Title;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.util.ObjectContracts;
import org.apache.causeway.applib.value.Clob;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.apache.causeway.persistence.jpa.applib.types.ClobJpaEmbeddable;

import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Document")
@DomainObject(bounding = Bounding.BOUNDED)
@DomainObjectLayout(
        cssClassFa = "file",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString
public class Document implements Comparable<Document> {

    private static MimeType MIME_TYPE = null;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @Title(prepend = "Object: ")
    @PropertyLayout(sequence = "1")
    @Column(nullable = false)
    @Getter
    @Setter
    private String title;

    @PropertyLayout(sequence = "2")
    @Column(nullable = false)
    @Getter
    @Setter
    private String docVersion; //SemVer

    @PropertyLayout(sequence = "3")
    @Column(nullable = true)
    @Getter
    @Setter
    private String url;

    //Column(nullable = false)
    @Embedded
    @Domain.Exclude
    private ClobJpaEmbeddable content;

    public String getContent() {
        return content.getChars();
    }

    public void setContent(String string) {
        content = ClobJpaEmbeddable.fromClob(new Clob("", MIME_TYPE, string.toCharArray()));
    }

    static {
        try {
            MIME_TYPE = new MimeType("text/plain");
        } catch (MimeTypeParseException e) {
            log.error(e.getMessage(), e);
        }
    }

    @PropertyLayout(sequence = "5")
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL)
    @Getter
    @Setter
    @ToString.Exclude
    private List<Sentence> sentences;

    @PropertyLayout(sequence = "6")
    @ManyToMany(mappedBy = "documents", cascade = CascadeType.ALL)
    @Getter
    @Setter
    @ToString.Exclude
    public List<Author> authors;

    @Column(nullable = false)
    @Getter
    @Setter
    private Timestamp createdAt;

    @Column(nullable = true)
    private Timestamp updatedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Document other) {
        return ObjectContracts.compare(this, other, "id");
    }
    //endregion

    @ManyToOne()
    @JoinColumn(name = "corpus_id")
    private Corpus corpus;

}
