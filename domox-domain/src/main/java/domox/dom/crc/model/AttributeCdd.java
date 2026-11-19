package domox.dom.crc.model;
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

@Entity(name = "PropertyCdd")
@Table(schema = DomainModule.SCHEMA, name = "PropertyCdd")
@Named(DomainModule.NAMESPACE + ".PropertyCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Property is a Member of a Class")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class AttributeCdd
        extends Candidate
        implements Comparable<AttributeCdd> {

    public AttributeCdd(String propertyName, String type) {
        this.setCandidateName(propertyName);
        this.type = type;
    }

    @Property
    @JoinColumn(nullable = false) // this always points to the owning class
    @ManyToOne
    public ClassCdd classCdd;

    /**
     * Owning analysis run.  Kept in parity with the other candidate types so a
     * {@link DomainModel} can cascade-remove every candidate it produced (even
     * ones whose owning {@link ClassCdd} lives in another model).
     */
    @Property
    @JoinColumn
    @ManyToOne
    public DomainModel domainModel;

    /**
     * Field to store the property type (e.g., "int", "String")
     * <p>
     * Only built-in/primitive types belong here.  A reference to <em>another class</em> of the model is
     * not an attribute but an association, and belongs on a {@link CollaboratorCdd} — otherwise the
     * attribute lexicon grows until it swallows the associations.
     */
    @Setter
    @Property
    @Column(nullable = false)
    public String type;

    @Override
    public int compareTo(@NotNull AttributeCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    public String toPlantUmlString() {
        return getCandidateName() + ": " + type;
    }
}
