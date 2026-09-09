package domox.dom.uml;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@Named(DomainModule.NAMESPACE + ".PropertyCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Property is a Member of a Class")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class PropertyCdd
        extends Candidate
        implements Comparable<PropertyCdd> {
    private Cardinality cardinality;

    public PropertyCdd(String propertyName, String type) {
        this.setCandidateName(propertyName);
        this.type = type;
    }

    @Property
    @JoinColumn(nullable = false) // this always points to the owning class
    @ManyToOne
    public ClassCdd classCdd;

    /**
     * Field to store the property type (e.g., "int", "String")
     * But not only primitives - class candidates from the scope of this analysis are to be set here as well
     */
    @Setter
    @Property
    @Column(nullable = false)
    public String type;

    @Override
    public int compareTo(@NotNull PropertyCdd o) {
        //FIXME
        return 0;
    }

    public String toPlantUmlString() {
        return getCandidateName() + ": " + type;
    }
}
