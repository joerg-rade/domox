package domox.dom.nlp;

import domox.dom.rqm.Document;
import lombok.Data;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.value.Clob;

import javax.jdo.annotations.*;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE,
        schema = "domox",
        table = "PartOfSpeech"
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
                        + "FROM domox.dom.nlp.PartOfSpeech "),
        @Query(
                name = "findByIdContains", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.PartOfSpeech "
                        + "WHERE id.indexOf(:id) >= 0 "),
        @Query(
                name = "findById", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.PartOfSpeech "
                        + "WHERE id == :id ")
})
@Unique(name = "PartOfSpeech_id_UNQ", members = {"id"})
@DomainObject(
        editing = Editing.DISABLED
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
@Data
public class PartOfSpeech implements Comparable<PartOfSpeech> {

    @Column(allowsNull = "false")
    @Property()
    private Long id;

    @Column(allowsNull = "false")
    @Property()
    private String content;

    @Column(allowsNull = "false")
    @Property()
    private PosType type;

    //region > compareTo, toString
    @Override
    public int compareTo(final PartOfSpeech other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
