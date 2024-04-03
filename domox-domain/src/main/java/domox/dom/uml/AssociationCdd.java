package domox.dom.uml;

import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

enum AssociationType {
    AGGREGATION,
    GENERALIZATION,
    ATTRIBUTE;
}

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.AssociationCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
//@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor
@Data
public class AssociationCdd
        extends Candidate
        implements Comparable<AssociationCdd> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Property
    @Column(nullable = false)
    private AssociationType type;

    @Property
    @Column(nullable = false)
    private Integer sourceCardinality;

    @Property
    @Column(nullable = false)
    private Integer targetCardinality;

    @Override
    public int compareTo(@NotNull AssociationCdd o) {
        return 0; //FIXME
    }
}
