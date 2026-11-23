package domox.dom.crc.model;
import domox.DomainModule;
import generate.PumlCode;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "ClassCdd")
@Named(DomainModule.NAMESPACE + ".ClassCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@NoArgsConstructor
public class ClassCdd
        extends Candidate
        implements Comparable<ClassCdd> {

    public ClassCdd(
            String name,
            List<AttributeCdd> attributeList,
            List<ResponsibilityCdd> responsibilityList,
            List<CollaboratorCdd> collaboratorList) {
        this.setCandidateName(name);
        this.attributeList = attributeList;
        this.responsibilityList = responsibilityList;
        this.collaboratorList = collaboratorList;
    }

    @Property
    @JoinColumn(nullable = false)
    @ManyToOne()
    public DomainModel domainModel;

    /**
     * The Coad archetype this class was classified as.
     * <p>
     * A public field is the property — Causeway maps it directly. It must <b>not</b> also carry a
     * Lombok {@code @Getter}: a public field plus a getter of the same name is two JAXB properties,
     * and Causeway serialises a list of domain objects with JAXB, so the duplicate makes every
     * RestfulObjects list endpoint fail with HTTP 500
     * ({@code Class has two properties of the same name "classType"}). {@code @Setter} is kept — it
     * is genuinely called by {@code CorpusIngestion}.
     */
    @Setter
    @Programmatic
    public ClassType classType = ClassType.PARTY_PLACE_THING;

     /**
     * The subdomain (business / functional area) this class candidate belongs to.
     * <p>
     * Set by the reviewer during the review workflow: as a {@code @ManyToOne} to the managed
     * {@link SubDomain} reference, Causeway renders it as an inline-editable dropdown on the
     * candidate's object page ({@code ClassCdd} opts into {@code editing = Editing.ENABLED}),
     * letting the reviewer pick from the existing subdomains.
     */
    @Property
    @ManyToOne
    @JoinColumn(name = "subdomain_id")
    @Getter
    @Setter
    @PropertyLayout(
            named = "Sub-domain",
            describedAs = "Sub-domain this class candidate belongs to (set during review)",
            sequence = "2.1")
    private SubDomain subDomain;

    /**
     * The attributes extracted for this class.
     * <p>
     * Public field only — <b>no</b> Lombok {@code @Getter} (see {@link #classType}): the pair is a
     * JAXB duplicate property and breaks the RestfulObjects list endpoints. Callers read
     * {@code classCdd.attributeList} directly. Same applies to {@link #responsibilityList} and
     * {@link #collaboratorList}.
     */
    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<AttributeCdd> attributeList = new ArrayList<>();

    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<ResponsibilityCdd> responsibilityList = new ArrayList<>();

    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<CollaboratorCdd> collaboratorList = new ArrayList<>();

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    @Programmatic
    public void addResponsibility(ResponsibilityCdd action) {
        responsibilityList.add(action);
    }

    @Programmatic
    public void addCollaborator(CollaboratorCdd association) {
        collaboratorList.add(association);
        association.setClassCdd(this);
    }

    /**
     * Renders this class as a PlantUML {@code class} block — <b>review-aware</b>: only the members
     * that are part of the assembled model appear.
     *
     * <p><b>Why the filter lives here.</b>  The export used to render every member of every class
     * regardless of its review, so the diagram claimed 168 operation lines while only 85 were
     * approved, and 88 attribute lines while only 9 were approved
     * ({@code MODEL_UML_READINESS_REVIEW.md} §2.1).  "What is part of the model?" now has exactly one
     * answer — {@link #approvedAttributes()}/{@link #approvedResponsibilities()} — and the entity's
     * own rendering, the assembler ({@code generate.ClassDiagram}) and the empty-class sweep
     * ({@code DomainModels#rejectEmptyClasses}) all read that same answer.</p>
     *
     * <p>The body may legitimately be <em>empty</em>: a class whose only model content is a
     * collaboration is still a class of the model ({@link ModelContent#includedClasses(DomainModel)}),
     * and it is drawn as a box joined to its collaborator.  Whether such a class is emitted at all is
     * the assembler's decision, not this method's.</p>
     */
    @Programmatic
    public String toPlantUmlString() {
        PumlCode code = new PumlCode();
        code.addClass(getCandidateName());
        code.add(" #" + classType.colorCode);
        code.addBegin();
        for (AttributeCdd p : approvedAttributes()) {
            code.addProperty(p.toPlantUmlString());
        }
        for (ResponsibilityCdd a : approvedResponsibilities()) {
            code.addResponsibility(a.toPlantUmlString());
        }
        code.addEnd();
        return code.getCode();
    }

    // region > review-aware model content

    /**
     * The approved attributes as the UML export renders them — {@code name: type}, one line per
     * attribute — so a diagram that shows the members cannot drift from the PlantUML/PDF rendering.
     *
     * <p>This is what the D3 class diagram draws in a box's attribute compartment: the same
     * {@link AttributeCdd#toPlantUmlString()} the PDF uses, read from the same approved subset.</p>
     */
    @Programmatic
    public List<String> approvedAttributeLines() {
        return approvedAttributes().stream().map(AttributeCdd::toPlantUmlString).toList();
    }

    /** The approved responsibilities as the export renders them — {@code name(params): ret}; see {@link #approvedAttributeLines()}. */
    @Programmatic
    public List<String> approvedResponsibilityLines() {
        return approvedResponsibilities().stream().map(ResponsibilityCdd::toPlantUmlString).toList();
    }

    /**
     * The attributes of this class that were <b>approved</b> during review — the ones the UML
     * export is allowed to emit.
     *
     * <p>The export used to render every member of every class regardless of its review, which is
     * how the assembled model came to claim 168 operation lines when only 85 were approved
     * ({@code MODEL_UML_READINESS_REVIEW.md} §2.1).  Reading the approved subset here — rather than
     * filtering in the exporter — keeps the question "what is part of the model?" answerable in one
     * place, and lets the same answer drive both the diagram and the empty-class sweep.</p>
     *
     * <p>A {@code null} list (a hand-built or detached instance) counts as none.</p>
     */
    @Programmatic
    public List<AttributeCdd> approvedAttributes() {
        if (attributeList == null) {
            return List.of();
        }
        return attributeList.stream().filter(Candidate::isApproved).toList();
    }

    /** The <b>approved</b> responsibilities (operations) of this class — see {@link #approvedAttributes()}. */
    @Programmatic
    public List<ResponsibilityCdd> approvedResponsibilities() {
        if (responsibilityList == null) {
            return List.of();
        }
        return responsibilityList.stream().filter(Candidate::isApproved).toList();
    }

    /**
     * Whether this class carries approved content <b>of its own</b>: at least one approved attribute
     * or one approved responsibility.
     *
     * <p><b>Collaborations are deliberately not counted here.</b>  A class's own
     * {@link #collaboratorList} holds only the collaborations it <em>owns</em> — ingestion attaches
     * each one to its source alone ({@code CollaboratorCandidates}) — so a class that is merely the
     * <em>target</em> of a relation would look empty.  Whether a class is part of the assembled model
     * is therefore decided on the model, not on the class:
     * {@link ModelContent#includedClasses(DomainModel)} adds every class that participates in a
     * material collaboration, on either end.</p>
     *
     * <p>What this method <em>does</em> pin down is the asymmetry that makes an empty box detectable:
     * a class can be approved while every one of its members was rejected, and then it carries no
     * content of its own — which, together with no relation, is what
     * {@link ModelContent#emptyClasses(DomainModel)} rejects.</p>
     */
    @Programmatic
    public boolean hasOwnModelledContent() {
        return !approvedAttributes().isEmpty() || !approvedResponsibilities().isEmpty();
    }

    // endregion
}
