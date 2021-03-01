package domox.dom.nlp;

import lombok.Data;
import org.apache.isis.applib.annotation.*;

import javax.jdo.annotations.*;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE,
        schema = "domox",
        table = "ModelDependency"
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
                        + "FROM domox.dom.rqm.ModelDependency "),
        @Query(
                name = "findByIdContains", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.rqm.ModelDependency "
                        + "WHERE id.indexOf(:id) >= 0 "),
        @Query(
                name = "findById", language = "JDOQL",
                value = "SELECT "
                        + "FROM domox.dom.rqm.ModelDependency "
                        + "WHERE id == :id ")
})
@Unique(name = "ModelDependency_id_UNQ", members = {"id"})
@DomainObject(
        editing = Editing.DISABLED
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
@Data
public class ModelDependency implements Comparable<ModelDependency> {

    @Column(allowsNull = "false")
    @Property()
    private Long id;

    @Column(allowsNull = "false")
    @Property()
    private ModelType type;

    @Column(allowsNull = "false")
    @Property()
    private PartOfSpeech partA;

    @Column(allowsNull = "false")
    @Property()
    private PartOfSpeech partB;

    //region > compareTo, toString
    @Override
    public int compareTo(final ModelDependency other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
