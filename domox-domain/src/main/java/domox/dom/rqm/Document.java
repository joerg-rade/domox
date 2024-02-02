package domox.dom.rqm;

import domox.dom.nlp.Sentence;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.value.Clob;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.apache.causeway.persistence.jpa.applib.types.ClobJpaEmbeddable;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.inject.Named;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.Set;

@Slf4j
@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.Document")
@DomainObject(bounding = Bounding.BOUNDED)
@DomainObjectLayout(cssClassFa = "file")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
public class Document implements Comparable<Document> {

    private static MimeType MIME_TYPE = null;

    @Id
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

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

    @Column(nullable = false)
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
    @OneToMany(mappedBy = "document", cascade = CascadeType.PERSIST)
    @Getter
    @Setter
    private Set<Sentence> sentences;

    @PropertyLayout(sequence = "6")
    @ManyToMany(mappedBy = "documents", cascade = CascadeType.PERSIST)
    @Getter
    @Setter
    public Set<Author> authors;

    @Column(nullable = false)
    @Getter
    @Setter
    private Timestamp createdAt;

    @Column(nullable = true)
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

    @ManyToOne()
    @JoinColumn(name = "corpus_id")
    private Corpus corpus;

}
