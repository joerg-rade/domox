package domox.dom.rqm;

import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.title.TitleService;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.persistence.CascadeType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Set;

@Component
@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "Author_eMail_UNQ", columnNames = {"eMail"})
        }
)
@javax.persistence.NamedQueries({
        @javax.persistence.NamedQuery(
                name = Author.NAMED_QUERY__FIND_BY_LASTNAME_LIKE,
                query = "SELECT o " +
                        "FROM Author o " +
                        "WHERE o.lastName LIKE :lastName"
        )
})
@javax.persistence.EntityListeners(CausewayEntityListener.class)
@DomainObject(nature = Nature.ENTITY, logicalTypeName = "domox.Author", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout()
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Author implements Comparable<Author> {

    @Inject
    @javax.persistence.Transient
    RepositoryService repositoryService;
    @Inject
    @javax.persistence.Transient
    TitleService titleService;
    @Inject
    @javax.persistence.Transient
    MessageService messageService;

    static final String NAMED_QUERY__FIND_BY_LASTNAME_LIKE = "Author.findByLastNameLike";
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(name = "id", nullable = false)
    private Long id;

    @javax.persistence.Version
    @javax.persistence.Column(nullable = false)
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

    @javax.persistence.ManyToMany(cascade = CascadeType.PERSIST)
    @javax.persistence.JoinTable(
            name = "author_document",
            joinColumns = @javax.persistence.JoinColumn(name = "author_id", nullable = true),
            inverseJoinColumns = @javax.persistence.JoinColumn(name = "document_id"))
    public Set<Document> documents;

}
