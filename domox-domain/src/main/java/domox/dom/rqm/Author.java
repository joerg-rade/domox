package domox.dom.rqm;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;

import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA)
@Named(DomainModule.NAMESPACE + ".Author")
@DomainObject(bounding = Bounding.BOUNDED)
@DomainObjectLayout(cssClassFa = "edit", describedAs = "An A. is the creator of a Document")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Author extends AbstractEntity implements Comparable<Author> {

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
            schema = "domox",
            joinColumns = @JoinColumn(name = "author_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    public List<Document> documents;

}
