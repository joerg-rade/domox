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
     * The stored value of {@link #outputType} for an operation that has no return type — the UML way
     * of writing "returns nothing", and the value the column has held since the first analysis run.
     * See {@link #setOutputType(String)} for why the "no answer" case is stored rather than left null.
     */
    private static final String NO_RETURN_TYPE = "void";

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
        setOutputType(outputType);
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
    private String outputType;

    /**
     * Sets the operation's UML return type, normalising "no return type" to the stored
     * {@link #NO_RETURN_TYPE} literal.
     *
     * <p><b>Why the normalisation lives here.</b> The column is {@code NOT NULL}, while
     * {@code ResponsibilityCandidates#outputTypeFor} answers the third of its three cases — the
     * verb is neither a receive verb nor an output verb, which is most verbs — with a deliberate
     * {@code null} ("the mapping had no answer", see that method's note). Writing that answer
     * straight through failed the <em>whole</em> analysis phase of a corpus load at the first
     * operation with a plain verb ({@code groom}):
     * {@code null value in column "outputtype" of relation "responsibilitycdd" violates not-null
     * constraint}. Normalising in the setter — rather than at each call site — is what makes the
     * invariant hold for every writer, including the
     * {@link #ResponsibilityCdd(String, List, String)} constructor and a detached instance built by
     * hand.</p>
     *
     * <p><b>Why {@code void} is the right stored value.</b> It is what the column already holds for
     * every row ever written, and it is indistinguishable from {@code null} and blank to
     * {@link #toPlantUmlString()}, so the rendered UML is unchanged; storing it keeps one
     * representation in the table instead of a mix of {@code void} and {@code null} for the same
     * fact.</p>
     */
    public void setOutputType(final String outputType) {
        this.outputType = (outputType == null || outputType.isBlank()) ? NO_RETURN_TYPE : outputType;
    }

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
        if (outputType != null && !outputType.isBlank() && !NO_RETURN_TYPE.equals(outputType)) {
            s.append(": ").append(outputType);
        }
        return s.toString();
    }
}
