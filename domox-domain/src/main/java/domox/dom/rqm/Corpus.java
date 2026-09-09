package domox.dom.rqm;

import domox.DomainModule;
import domox.dom.AbstractEntity;
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
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.annotation.TableDecorator;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA)
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
    private List<Document> documents;

    @PropertyLayout(sequence = "2")
    @Column(nullable = false)
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
        this.documents.add(document);
//        repositoryService.persistAndFlush(obj);
//        repositoryService.persistAndFlush(document);
    }
}
