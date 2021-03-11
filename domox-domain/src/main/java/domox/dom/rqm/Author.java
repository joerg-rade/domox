package domox.dom.rqm;

import domox.SimpleModule;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.val;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.services.message.MessageService;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.services.title.TitleService;
import org.apache.isis.persistence.jpa.applib.integration.JpaEntityInjectionPointResolver;

import javax.inject.Inject;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Set;

@javax.persistence.Entity
@javax.persistence.Table(
        schema = "domox",
        uniqueConstraints = {
                @javax.persistence.UniqueConstraint(name = "Author_eMail_UNQ", columnNames = {"eMail"})
        }
)
@javax.persistence.EntityListeners(JpaEntityInjectionPointResolver.class) // injection support
@DomainObject(objectType = "domox.Author", editing = Editing.DISABLED)
@DomainObjectLayout(cssClassFa = "user")
@NoArgsConstructor
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Author implements Comparable<domox.dom.rqm.Author> {

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

    public static class ActionDomainEvent extends SimpleModule.ActionDomainEvent<Author> {
    }

    @Inject
    @javax.persistence.Transient
    RepositoryService repositoryService;
    @Inject
    @javax.persistence.Transient
    TitleService titleService;
    @Inject
    @javax.persistence.Transient
    MessageService messageService;

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
    @javax.persistence.ManyToMany
    public Set<Document> documents;

}
