package domox.dom.nlp;

import domox.Constants;
import domox.DomainModule;
import domox.dom.rqm.Document;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.extensions.pdfjs.applib.annotations.PdfJsViewer;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.apache.causeway.persistence.jpa.applib.types.BlobJpaEmbeddable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "Sentence")
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Sentence")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "dollar",
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
    @Getter
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 2048)
    @Property()
    @Getter
    @Setter
    private String text;

    @ElementCollection
    @CollectionTable(
            name = "SENTENCE_WORD",
            schema = "domox",
            joinColumns = @JoinColumn(name = "sentence_id"))
    @OrderColumn(name = "word_index")
    private List<String> words = new ArrayList<>();

    @Programmatic
    public String getWord(int index) {
        return index < words.size() ? words.get(index) : null;
    }

    // region PDF
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

    @Programmatic
    public void updateImageFromBytes(byte[] bytes, String filename) {
        final Blob blob = new Blob(filename, Constants.pdfMimeType, bytes);
        setAttachment(blob);
    }
    // endregion PDF

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

    @OneToMany(mappedBy = "sentence")
    @Getter
    @Setter
    private List<TypedDependency> typedDependencies;

    @Programmatic
    public void addTypedDependency(TypedDependency td) {
        if (this.typedDependencies == null) {
            this.typedDependencies = new ArrayList<>();
        }
        this.typedDependencies.add(td);
    }

    @Programmatic
    public TypedDependency previousTd(TypedDependency current) {
        int i = typedDependencies.indexOf(current);
        return i > 0 ? typedDependencies.get(i - 1) : null;
    }

    @Programmatic
    public TypedDependency nextTd(TypedDependency current) {
        int i = typedDependencies.indexOf(current);
        return (i >= 0 && i < typedDependencies.size() - 1) ? typedDependencies.get(i + 1) : null;
    }

}
