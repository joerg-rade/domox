package domox.dom.rqm;

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

import javax.inject.Named;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
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
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
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
