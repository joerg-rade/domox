package domox.dom.crc.model;
import domox.dom.AbstractEntity;
import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Title;
import org.jspecify.annotations.NonNull;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "SubDomain")
@Named(DomainModule.NAMESPACE + ".SubDomain")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "folder",
        describedAs = "A named business / functional sub-domain that class candidates can be assigned to during review")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class SubDomain extends AbstractEntity implements Comparable<SubDomain> {

    @Title
    public String title() {
        return name;
    }

    @Column(nullable = false)
    @Getter
    @Setter
    @ToString.Include
    @PropertyLayout(
            named = "Name",
            describedAs = "Name of the sub-domain (e.g. \"Checkout\", \"Inventory\")")
    private String name;

    /**
     * How this sub-domain contributes to the business — {@code CORE}, {@code SUPPORTING} or
     * {@code GENERIC}, and {@code UNCLASSIFIED} until a reviewer decides.
     * <p>
     * Stored as a string ({@link EnumType#STRING}): the value stays readable in the database and
     * reordering the enum cannot silently re-map the rows already written.  The field initialiser
     * (rather than a database default) is what makes a {@code new SubDomain()} well-defined before
     * any decision has been taken.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    @ToString.Include
    @PropertyLayout(
            named = "Classification",
            describedAs = "Core / supporting / generic — how this sub-domain contributes to the business",
            sequence = "2")
    private SubDomainClassification classification = SubDomainClassification.UNCLASSIFIED;

    @Override
    public int compareTo(@NonNull SubDomain o) {
        return Long.compare(this.getId(), o.getId());
    }
}