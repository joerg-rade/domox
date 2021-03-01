package domox.dom.rqm;

import domox.SimpleModule;
import lombok.Data;
import lombok.val;
import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.services.message.MessageService;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.services.title.TitleService;

import javax.inject.Inject;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.VersionStrategy;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@javax.jdo.annotations.PersistenceCapable(identityType = IdentityType.DATASTORE, schema = "domox")
@javax.jdo.annotations.DatastoreIdentity(strategy = IdGeneratorStrategy.IDENTITY, column = "id")
@javax.jdo.annotations.Version(strategy = VersionStrategy.DATE_TIME, column = "version")
@javax.jdo.annotations.Unique(name = "Author_name_UNQ", members = {"name"})
@DomainObject()
@DomainObjectLayout()
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@Data
public class Author implements Comparable<Author> {

    public static Author withName(String lastName) {
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
    RepositoryService repositoryService;
    @Inject
    TitleService titleService;
    @Inject
    MessageService messageService;

    private Author() {
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

}
