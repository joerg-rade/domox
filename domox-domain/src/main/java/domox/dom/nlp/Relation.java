package domox.dom.nlp;

import org.apache.causeway.applib.annotation.PropertyLayout;

import java.util.List;

public class Relation implements Comparable<Relation> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(name = "id", nullable = false)
    private Long id;

    @javax.persistence.Version
    @javax.persistence.Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @javax.persistence.Column(nullable = false)
    private RelationType type;

    //TODO cardinality 1:2
    @javax.persistence.OneToMany(mappedBy = "relation")
    private List<TypedDependency> modelDependencies;

    //region > compareTo, toString
    @Override
    public int compareTo(final Relation other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.causeway.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
