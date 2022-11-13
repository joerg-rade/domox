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
import javax.persistence.Embedded;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.Set;

@Slf4j
@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@javax.persistence.EntityListeners(CausewayEntityListener.class)
@Named("domox.Document")
@DomainObject(bounding = Bounding.BOUNDED)
@DomainObjectLayout(cssClassFa = "file")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
public class Document implements Comparable<Document> {

    private static MimeType MIME_TYPE = null;

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

    @javax.persistence.Column(nullable = false)
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
