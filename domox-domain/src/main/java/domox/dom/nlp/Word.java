package domox.dom.nlp;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.BookmarkPolicy;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.annotation.TableDecorator;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.Comparator;

@Entity
@Table(
        schema = DomainModule.SCHEMA,
        uniqueConstraints = {
                @UniqueConstraint(name = "PartOfSpeech_id_UNQ", columnNames = {"id"})
        }
)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Word")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "speech",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Getter @Setter
public class Word implements Comparable<Word> {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column()
    private int version;

    @Column(nullable = false)
    @Property()
    private String text;

    @Column(nullable = false)
    @Property()
    @Getter
    @Setter
    private PosType type;

    @ManyToOne()
    @JoinColumn(name = "sentence_id")
    private Sentence sentence;

    private final static Comparator<Word> comparator =
            Comparator.comparing(Word::getId);

    @Override
    public int compareTo(final Word other) {
        return comparator.compare(this, other);
    }

}
