package domox.dom.nlp;

import java.sql.Timestamp;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.DatastoreIdentity;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;
import javax.jdo.annotations.Unique;
import javax.jdo.annotations.Version;
import javax.jdo.annotations.VersionStrategy;

import domox.dom.rqm.Document;
import lombok.Data;
import org.apache.isis.applib.annotation.BookmarkPolicy;
import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.annotation.Editing;
import org.apache.isis.applib.annotation.Property;
import org.apache.isis.applib.value.Clob;

import lombok.Getter;
import lombok.Setter;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE,
        schema = "domox",
        table = "Sentence"
)
@DatastoreIdentity(
        strategy = IdGeneratorStrategy.IDENTITY,
        column = "id")
@Version(
        strategy = VersionStrategy.VERSION_NUMBER,
        column = "version")
@Queries({
        @Query(
                name = "find", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.Sentence "),
        @Query(
                name = "findByIdContains", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.Sentence "
                        + "WHERE id.indexOf(:id) >= 0 "),
        @Query(
                name = "findById", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.Sentence "
                        + "WHERE id == :id ")
})
@Unique(name = "Sentence_id_UNQ", members = {"id"})
@DomainObject(
        editing = Editing.DISABLED
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
@Data
public class Sentence implements Comparable<Sentence> {

    @Column(allowsNull = "false")
    @Property()
    private Long id;

    @Column(allowsNull = "false")
    @Property()
    private String raw;

    @Column(allowsNull = "true")
    @Property()
    private Clob parsed;

    @Column(allowsNull = "false")
    @Property()
    private Document document;

    //region > compareTo, toString
    @Override
    public int compareTo(final Sentence other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
