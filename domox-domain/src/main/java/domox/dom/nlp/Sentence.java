package domox.dom.nlp;

import domox.Constants;
import domox.DomainModule;
import domox.dom.AbstractEntity;
import domox.dom.rqm.Document;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.extensions.pdfjs.applib.annotations.PdfJsViewer;
import org.apache.causeway.persistence.jpa.applib.types.BlobJpaEmbeddable;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "Sentence")
@Named(DomainModule.NAMESPACE + ".Sentence")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "dollar",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class Sentence extends AbstractEntity implements Comparable<Sentence> {

    @Title
    public String title() {
        if (this.text == null) return "";
        int colonIndex = this.text.indexOf(':');
        return colonIndex >= 0 ? this.text.substring(0, colonIndex).strip() : this.text.strip();
    }

    @Column(nullable = false, length = 2048)
    @Property()
    @Getter
    @Setter
    private String text;

    // region PDF
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "diagram_name")),
            @AttributeOverride(name = "mimeType", column = @Column(name = "diagram_mimeType")),
            @AttributeOverride(name = "bytes", column = @Column(name = "diagram_bytes", columnDefinition = "BYTEA"))
    })
    @Embedded
    private BlobJpaEmbeddable diagram;

    @PdfJsViewer
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(named = "Syntax Diagram", fieldSetId = "content", sequence = "1")
    public Blob getDiagram() {
        return diagram != null ? BlobJpaEmbeddable.toBlob(diagram) : null;
    }

    public void setDiagram(final Blob diagram) {
        this.diagram = BlobJpaEmbeddable.fromBlob(diagram);
    }

    @Programmatic
    public void updateImageFromBytes(byte[] bytes, String filename) {
        final Blob blob = new Blob(filename, Constants.pdfMimeType, bytes);
        setDiagram(blob);
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
        return Long.compare(this.getId(), other.getId());
    }
    //endregion

    @OneToMany(mappedBy = "sentence", cascade = CascadeType.ALL)
    @OrderBy("dependentIndex ASC")
    @Getter
    @Setter
    private List<TypedDependency> typedDependencies;

    @Programmatic
    public void addTypedDependency(TypedDependency td) {
        if (this.typedDependencies == null) {
            this.typedDependencies = new ArrayList<>();
        }
        this.typedDependencies.add(td);
        this.typedDependencies.sort(
                java.util.Comparator.comparingInt(TypedDependency::getDependentIndex));
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
