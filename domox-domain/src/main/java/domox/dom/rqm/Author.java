package domox.dom.rqm;

import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.val;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.Set;

@Entity
@Table(
        schema = "domox",
        uniqueConstraints = {
                @UniqueConstraint(name = "Author_eMail_UNQ", columnNames = {"eMail"})
        }
)
@NamedQueries({
        @NamedQuery(
                name = Author.NAMED_QUERY__FIND_BY_LASTNAME_LIKE,
                query = "SELECT o " +
                        "FROM Author o " +
                        "WHERE o.lastName LIKE :lastName"
        )
})
@EntityListeners(CausewayEntityListener.class)
@Named("domox.Author")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout()
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Author implements Comparable<Author> {

    static final String NAMED_QUERY__FIND_BY_LASTNAME_LIKE = "Author.findByLastNameLike";

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    @Getter
    @Setter
    private long version;

    public static Author withLastName(String lastName) {
        val o = new Author();
        o.setLastName(lastName);
        return o;
    }

    @Override
    public int compareTo(Author o) {
        return 0;
    }

    @ToString.Include
    public String title() {
        return "Object: " + getFirstName() +
                getMiddleInitial() +
                getLastName();
    }

    @Getter
    @Setter
    private String firstName;
    @Getter
    @Setter
    private String middleInitial;
    @Getter
    @Setter
    private String lastName;
    @Getter
    @Setter
    @ToString.Include
    private String eMail;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "author_document",
            joinColumns = @JoinColumn(name = "author_id", nullable = true),
            inverseJoinColumns = @JoinColumn(name = "document_id"))
    public Set<Document> documents;

}
