package domox.dom.crc.model;
import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Publishing;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "ResponsibilityCdd")
@Table(schema = DomainModule.SCHEMA, name = "ResponsibilityCdd")
@Named(DomainModule.NAMESPACE + ".ResponsibilityCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "bolt")
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class ResponsibilityCdd
        extends Candidate
        implements Comparable<ResponsibilityCdd> {

    /**
     * @param name          the operation name
     * @param inputTypeList the operation's parameters; a {@code null} list is normalised to an empty
     *                      one, and the list is <em>copied</em>, because the caller may pass an
     *                      immutable list ({@code List.of()}) and this collection is appended to when
     *                      a parameter is added later
     * @param outputType    the UML return type; {@code null}/blank/{@code void} renders no return type
     */
    public ResponsibilityCdd(String name, List<ResponsibilityParameter> inputTypeList, String outputType) {
        this.setCandidateName(name);
        this.inputTypeList = inputTypeList != null ? new ArrayList<>(inputTypeList) : new ArrayList<>();
        this.outputType = outputType;
    }

    @Property
    @JoinColumn
    @ManyToOne
    public ClassCdd classCdd;

    /**
     * Owning analysis run.  Unlike {@link #classCdd}, this is always set even
     * for orphan candidates (e.g. actions whose owning class is a blocked
     * use-case noun), so the whole analysis can be cascade-removed with its
     * {@link DomainModel}.
     */
    @Property
    @JoinColumn
    @ManyToOne
    public DomainModel domainModel;

    @OneToMany(mappedBy = "responsibilityCdd", cascade = CascadeType.ALL)
    @Collection
    public List<ResponsibilityParameter> inputTypeList = new ArrayList<>();

    @Property
    @Column(nullable = false)
    @Setter
    private String outputType;

    @Override
    public int compareTo(@NotNull ResponsibilityCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    /**
     * Renders this responsibility as a PlantUML operation line, e.g. {@code browse()} or
     * {@code notify(message: String)}.
     * <p>
     * The closing parenthesis is appended <em>unconditionally</em>: the previous implementation only
     * emitted it from a {@code replaceAll(", $" → ")")} that stripped a trailing separator, so an
     * operation with no parameters rendered as {@code browse(: void} — invalid PlantUML, and the
     * shape of every one of the 149 operation lines in the exported model. A {@code null}
     * {@code inputTypeList} (possible for a hand-built or detached instance) is treated as no
     * parameters rather than throwing, and a {@code null}/{@code void} return type is omitted
     * entirely, because UML writes no return type for {@code void}.
     */
    public String toPlantUmlString() {
        final StringBuilder s = new StringBuilder(getCandidateName()).append('(');
        if (inputTypeList != null) {
            String separator = "";
            for (final ResponsibilityParameter parameter : inputTypeList) {
                s.append(separator).append(parameter.toPlantUmlString());
                separator = ", ";
            }
        }
        s.append(')');
        if (outputType != null && !outputType.isBlank() && !"void".equals(outputType)) {
            s.append(": ").append(outputType);
        }
        return s.toString();
    }
}
