package domox.dom.uml;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".AssociationCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor
public class AssociationCdd
        extends Candidate
        implements Comparable<AssociationCdd> {

    public AssociationCdd(String name, ClassCdd source, ClassCdd target) {
        this.name = name;
        this.source = source;
        this.target = target;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Setter
    @Property
    @Column(nullable = false)
    private ClassCdd source;
    @Setter
    @Property
    @Column(nullable = false)
    private ClassCdd target;

    @Setter
    @Property
    @Column(nullable = false)
    private AssociationType type;

    @Setter
    @Property
    @Column(nullable = false)
    private String sourceCardinality = "";

    @Setter
    @Property
    @Column(nullable = false)
    private String targetCardinality = "";

    @Override
    public int compareTo(@NotNull AssociationCdd o) {
        return 0; //FIXME
    }

    public String toPlantUmlString() {
        final String arrow = AssociationType.ASSOCIATION.symbol;
        final String relation = quote(sourceCardinality) + arrow + quote(targetCardinality);
        return source.getName() + relation + target.getName() + ": " + name;
    }

    private String quote(String s) {
        if (s.isBlank()) return s;
        return "\"" + s + "\"";
    }
}
