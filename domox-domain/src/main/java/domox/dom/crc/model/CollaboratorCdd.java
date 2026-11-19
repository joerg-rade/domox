package domox.dom.crc.model;
import domox.DomainModule;
import domox.dom.rules.AssociationLabel;
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

@Entity(name = "AssociationCdd")
@Table(schema = DomainModule.SCHEMA, name = "AssociationCdd")
@Named(DomainModule.NAMESPACE + ".AssociationCdd")
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

    @Setter
    @Property
    @OneToOne
    private ClassCdd source;

    @Setter
    @Property
    @OneToOne
    private ClassCdd target;

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

    public String toPlantUmlString() {
        final AssociationType effectiveType = type != null ? type : AssociationType.ASSOCIATION;
        final String arrow = effectiveType.symbol;
        final String relation = quote(sourceCardinality) + arrow + quote(targetCardinality);
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

    private String quote(String s) {
        if (s.isBlank()) return s;
        return "\"" + s + "\"";
    }
}
