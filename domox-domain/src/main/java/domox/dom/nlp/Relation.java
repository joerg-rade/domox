package domox.dom.nlp;

import lombok.Data;
import org.apache.isis.applib.annotation.*;

import javax.jdo.annotations.*;
import java.util.List;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE,
        schema = "domox",
        table = "Relation"
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
                        + "FROM domox.dom.nlp.Relation "),
        @Query(
                name = "findByIdContains", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.Relation "
                        + "WHERE id.indexOf(:id) >= 0 "),
        @Query(
                name = "findById", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.nlp.Relation "
                        + "WHERE id == :id ")
})
@Unique(name = "Relation_id_UNQ", members = {"id"})
@DomainObject(
        editing = Editing.DISABLED
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
@Data
public class Relation implements Comparable<Relation> {

    @Column(allowsNull = "false")
    @Property()
    private Long id;

    @Column(allowsNull = "false")
    @Property()
    private RelationType type;

    @Column(allowsNull = "false") // cardinality 1:2
    @Property()
    private List<ModelDependency> modelDependencies;

    //region > compareTo, toString
    @Override
    public int compareTo(final Relation other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
