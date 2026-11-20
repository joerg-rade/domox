package domox.dom.crc.model;
import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import org.apache.causeway.applib.annotation.Property;
import org.jetbrains.annotations.NotNull;

@Entity(name = "ResponsibilityParameter")
@Table(schema = DomainModule.SCHEMA, name = "ResponsibilityParameter")
@Named(DomainModule.NAMESPACE + ".ResponsibilityParameter")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Parameter is the Type of an argument of an Action")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class ResponsibilityParameter
        extends Candidate
        implements Comparable<ResponsibilityParameter> {

    public ResponsibilityParameter(String name, String type) {
        this.setCandidateName(name);
        this.type = type;
    }

    /**
     * The responsibility (operation) this parameter belongs to — the inverse of
     * {@link ResponsibilityCdd#inputTypeList}.
     * <p>
     * Public field only — <b>no</b> Lombok {@code @Getter}: a public field plus a getter of the same
     * name is two JAXB properties and breaks the RestfulObjects list endpoints with HTTP 500
     * ({@code Class has two properties of the same name "responsibilityCdd"}).
     */
    @Setter
    @ManyToOne()
    @JoinColumn(name = "responsibilitycdd_id", nullable = false)
    @Property()
    public ResponsibilityCdd responsibilityCdd;

    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private String type;

    @Override
    public int compareTo(@NotNull ResponsibilityParameter o) {
        return Long.compare(this.getId(), o.getId());
    }

    public String toPlantUmlString() {
        return getCandidateName() + ": " + type;
    }
}
