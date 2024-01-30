package domox.dom.uml;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

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
//@EqualsAndHashCode(exclude = {"cronExpression", "active", "executionList", "queryClassName"})
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
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
