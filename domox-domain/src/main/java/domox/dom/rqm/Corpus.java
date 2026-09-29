package domox.dom.rqm;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import domox.dom.crc.DomainModel;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.BookmarkPolicy;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.annotation.TableDecorator;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "Corpus")
@Named(DomainModule.NAMESPACE + ".Corpus")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "files-o",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Corpus extends AbstractEntity implements Comparable<Corpus> {

    @Column(nullable = true)
    @Getter
    @Setter
    private String title;

    @PropertyLayout(sequence = "1")
    @OneToMany(mappedBy = "corpus", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    private List<Document> documents;

    /**
     * The single {@link DomainModel} shared by <em>all</em> documents in this
     * corpus.  Because the whole use-case suite writes into one model,
     * candidate names de-duplicate across documents instead of being re-created
     * once per document (which previously duplicated the entire candidate set
     * N times for N documents).  The model's lifecycle (cascade + orphan
     * removal) is owned by the {@code Corpus}, not by any single {@code Document}.
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "domain_model_id")
    @Programmatic
    @Getter
    @Setter
    private DomainModel domainModel;

    @PropertyLayout(sequence = "2")
    @Column(nullable = false)
    @Getter
    @Setter
    private Timestamp analyzedAt;

    //region > compareTo, toString
    @Override
    public int compareTo(final Corpus other) {
        return Long.compare(this.getId(), other.getId());
    }

    @Override
    public String toString() {
        return "Corpus{" +
                "id=" + getId() +
                ", title='" + title + '\'' +
                '}';
    }
    //endregion

    //TODO reference repositoryService here or delegate to Factory ?
    public void addDocument(Document document) {
        // Keep the bidirectional association consistent: the owning side is the
        // document's corpus FK, so set it here as well as adding to the collection.
        document.setCorpus(this);
        this.documents.add(document);
//        repositoryService.persistAndFlush(obj);
//        repositoryService.persistAndFlush(document);
    }
}
