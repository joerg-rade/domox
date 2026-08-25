package domox.dom.nlp;

import domox.Constants;
import domox.DomainModule;
import domox.dom.rqm.Document;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.extensions.pdfjs.applib.annotations.PdfJsViewer;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.apache.causeway.persistence.jpa.applib.types.BlobJpaEmbeddable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.causeway.applib.annotation.SemanticsOf.IDEMPOTENT;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "Sentence")
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Sentence")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "paragraph",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Sentence implements Comparable<Sentence> {

    private static final Logger log = LoggerFactory.getLogger(Sentence.class);

    @Title
    public String title() {
        return this.document.getTitle() + ".S" + this.id + "." + this.version;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 20)
    @Property()
    @Getter
    @Setter
    private PartOfSpeechType type;

    @Column(nullable = false, length = 2048)
    @Property()
    @Getter
    @Setter
    private String text;

    @OneToMany(mappedBy = "sentence", cascade = CascadeType.ALL)
    @Getter
    @Setter
    private List<Token> tokenList = new ArrayList<>();

    // start PDF
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "attachment_name")),
            @AttributeOverride(name = "mimeType", column = @Column(name = "attachment_mimeType")),
            @AttributeOverride(name = "bytes", column = @Column(name = "attachment_bytes", columnDefinition = "BYTEA"))
    })
    @Embedded
    private BlobJpaEmbeddable attachment;

    @PdfJsViewer
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(fieldSetId = "content", sequence = "1")
    public Blob getAttachment() {
        return attachment != null ? BlobJpaEmbeddable.toBlob(attachment) : null;
    }

    public void setAttachment(final Blob attachment) {
        this.attachment = BlobJpaEmbeddable.fromBlob(attachment);
    }

    @Action(semantics = IDEMPOTENT, commandPublishing = Publishing.ENABLED, executionPublishing = Publishing.ENABLED)
    @ActionLayout(associateWith = "attachment", position = ActionLayout.Position.PANEL)
    public Sentence updateAttachment(
            @Nullable final Blob attachment) {
        setAttachment(attachment);
        return this;
    }

    @MemberSupport
    public Blob default0UpdateAttachment() {
        return getAttachment();
    }

    @Programmatic
    public void updateImageFromBytes(byte[] bytes, String filename) {
        final Blob blob = new Blob(filename, Constants.pdfMimeType, bytes);
        setAttachment(blob);
    }
    // end PDF

    @ManyToOne()
    @JoinColumn(name = "documentId")
    @Property()
    @Getter
    @Setter
    private Document document;

    //region > compareTo, toString
    @Override
    public int compareTo(final Sentence other) {
        return Long.compare(this.id, other.id);
    }
    //endregion

    @Programmatic
    public Token getToken(int index) {
        final List<Token> tokens = new ArrayList<>(this.tokenList);
        if (index >= tokens.size()) {
            log.debug("index >= tokenList");
            return null;
        }
        return tokens.get(index);
    }

    @OneToMany(mappedBy = "sentence")
    private Collection<TypedDependency> typedDependency;

    public Collection<TypedDependency> getTypedDependency() {
        return typedDependency;
    }

    public void setTypedDependency(Collection<TypedDependency> typedDependency) {
        this.typedDependency = typedDependency;
    }
}
