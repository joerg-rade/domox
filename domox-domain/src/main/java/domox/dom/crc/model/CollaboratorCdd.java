package domox.dom.crc.model;
import domox.DomainModule;
import domox.dom.rules.lexicon.AssociationLabel;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.jetbrains.annotations.NotNull;

@Entity(name = "CollaboratorCdd")
@Table(schema = DomainModule.SCHEMA, name = "CollaboratorCdd")
@Named(DomainModule.NAMESPACE + ".CollaboratorCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@NoArgsConstructor
public class CollaboratorCdd
        extends Candidate
        implements Comparable<CollaboratorCdd> {

    public CollaboratorCdd(String name, ClassCdd source, ClassCdd target) {
        this.setCandidateName(name);
        this.source = source;
        this.target = target;
    }

    @Setter
    @Property
    @JoinColumn(nullable = false)
    @ManyToOne
    private ClassCdd classCdd;

    /**
     * Owning analysis run.  Always set during {@code analyzeDocument()} so the
     * whole analysis can be cascade-removed with its {@link DomainModel}.
     */
    @Setter
    @Property
    @JoinColumn
    @ManyToOne
    private DomainModel domainModel;

    /**
     * The association's source end.  A <em>private</em> field, so a Lombok getter is safe here —
     * unlike the public-field members in {@link ClassCdd}/{@link DomainModel}, where a field and a
     * getter of the same name would be two JAXB properties (see {@link #sourceCardinality}).
     */
    @Getter
    @Setter
    @Property
    @OneToOne
    private ClassCdd source;

    /** The association's target end — see {@link #source}. */
    @Getter
    @Setter
    @Property
    @OneToOne
    private ClassCdd target;

    /**
     * The association's UML kind.  A <em>private</em> field, so it carries a Lombok getter like its
     * {@link #source}/{@link #target} neighbours do — a <em>public</em> field plus a getter of the same
     * name would be two JAXB properties (see {@link #source}), and every renderer has to be able to
     * <em>read</em> the kind to draw an inheritance as an inheritance rather than as a plain
     * association.
     */
    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private AssociationType type;

    /**
     * UML multiplicity of the {@link #source} end ({@code "1"}, {@code "0..*"}, …), derived from the
     * sentence the association was extracted from by {@link MultiplicityEvidence}; {@code ""} when
     * that sentence states none.  Private field, so a getter here is safe — unlike the public-field
     * members in {@link ClassCdd}/{@link DomainModel}, where field and getter would be two JAXB
     * properties of the same name.
     */
    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private String sourceCardinality = "";

    /** UML multiplicity of the {@link #target} end — see {@link #sourceCardinality}. */
    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private String targetCardinality = "";

    @Override
    public int compareTo(@NotNull CollaboratorCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    /**
     * This association as one PlantUML relation line — {@code Address == Recipient: Address_Recipient}.
     *
     * <p><b>The spaces around the arrow are load-bearing, not cosmetic.</b>  PlantUML reads a letter
     * {@code d}/{@code l}/{@code r}/{@code u}/{@code x} placed immediately after a <em>headless</em>
     * arrow as a <em>direction</em> specifier ({@code A ==r B}), so an arrow butted against the
     * target's name costs that name its first character and PlantUML invents a second class from the
     * remainder: {@code Address==Recipient} rendered a phantom class {@code ecipient} and aimed the
     * {@code Address_Recipient} association at it, so the rendered diagram disagreed with the D3 view
     * about which classes the model has.  {@link AssociationType#SYNONYM} is the only headless symbol
     * in this enum — the arrows that carry a head ({@code ->}, {@code --|>}) were never affected —
     * but every symbol is padded, because the hazard belongs to PlantUML's grammar rather than to one
     * arrow.  Verified against Kroki for all seven symbols × all ten letters, with and without
     * multiplicities.</p>
     */
    public String toPlantUmlString() {
        final AssociationType effectiveType = type != null ? type : AssociationType.ASSOCIATION;
        final String relation = paddedQuote(sourceCardinality)
                + effectiveType.symbol
                + paddedQuote(targetCardinality);
        return source.getCandidateName() + relation + target.getCandidateName() + ": " + label();
    }

    /**
     * The label drawn on the association's edge: the verb the association was extracted from
     * ({@code "has"}, {@code "enroll in"}) when a rule match records one, otherwise the candidate's own
     * name ({@code Customer_Premium}).
     * <p>
     * Derived from the candidate's own rule matches rather than stored on the entity — see
     * {@link AssociationLabel}.
     */
    @Programmatic
    public String label() {
        return AssociationLabel.of(getRuleMatches(), getCandidateName());
    }

    /**
     * Whether this association is <b>material</b> — approved itself <em>and</em> drawn between two
     * approved classes.
     *
     * <p>An association with one unapproved end states nothing about the model: a generalization
     * from an approved class to a name the reviewer threw away is not a generalization of the model.
     * The review policy already requires both ends to be approved before an association is approved
     * ({@code REVIEW_PLAYBOOK.md} §4.4), so this predicate is the invariant the export relies on
     * rather than a second, weaker rule — it is what keeps the exporter correct for a hand-edited
     * or half-reviewed database.</p>
     *
     * <p>A {@code null} end (possible for a hand-built or detached instance) is not material.</p>
     */
    @Programmatic
    public boolean connectsApprovedClasses() {
        return isApproved()
                && source != null && source.isApproved()
                && target != null && target.isApproved();
    }

    private String quote(String s) {
        if (s.isBlank()) return s;
        return "\"" + s + "\"";
    }

    /**
     * The multiplicity as PlantUML writes it — quoted, and padded on <em>both</em> sides so the arrow
     * can never touch an identifier (see {@link #toPlantUmlString()}); a single space when the model
     * states no multiplicity, so the relation reads {@code A == B} rather than {@code A  ==  B}.
     */
    private String paddedQuote(String cardinality) {
        if (cardinality.isBlank()) return " ";
        return " " + quote(cardinality) + " ";
    }
}
