package domox.dom.rqm;

import lombok.*;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;

import javax.persistence.CascadeType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Set;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "Author_eMail_UNQ", columnNames = {"eMail"})
        }
)
@DomainObject(nature=Nature.ENTITY, logicalTypeName = "domox.Author", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "user")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Author implements Comparable<Author> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    @Programmatic
    private Long id;

    @javax.persistence.Version
    @Programmatic
    @javax.persistence.Column(nullable = false)
    private int version;

    public static Author withLastName(String lastName) {
        val o = new Author();
        o.setLastName(lastName);
        return o;
    }

    @Override
    public int compareTo(Author o) {
        return 0;
    }

    public String title() {
        return "Object: " + getFirstName() +
                getMiddleInitial() +
                getLastName();
    }

    private String firstName;
    private String middleInitial;
    private String lastName;
    private String eMail;

    @Property()
    @javax.persistence.ManyToMany(cascade = CascadeType.PERSIST)
    @javax.persistence.JoinTable(
            name = "author_document",
            joinColumns = @javax.persistence.JoinColumn(name = "author_id", nullable = true),
            inverseJoinColumns = @javax.persistence.JoinColumn(name = "document_id"))
    public Set<Document> documents;

}
