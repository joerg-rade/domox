package domox.dom.crc.model;
import domox.dom.AbstractEntity;
import domox.DomainModule;
import domox.dom.crc.diagram.DomainModelDiagramsD3;
import domox.dom.requirements.Corpora;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Entity
@Table(schema = DomainModule.SCHEMA, name = "DomainModel")
@Named(DomainModule.NAMESPACE + ".DomainModel")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "road",
        describedAs = "A domain model: the classes, attributes, responsibilities and collaborators "
                + "analysed from a corpus of use-case documents")
@NoArgsConstructor
public class DomainModel extends AbstractEntity implements Comparable<ClassCdd> {

    /**
     * A one-line identity for the model: <em>who owns it</em> and <em>how big it is</em>.
     *
     * <p><b>Why it is derived rather than stored.</b>  The model has no name column of its own — its
     * identity <em>is</em> the corpus it belongs to, and the owning side of that association lives
     * on {@link domox.dom.requirements.Corpus} ({@code domain_model_id}).  So the owner is looked up
     * through the injected {@link Corpora} service, and the size is read from the model's own
     * candidate collections.</p>
     *
     * <p><b>What it makes visible.</b>  A model created by hand from {@code DomainModels#create()}
     * owns no corpus and no candidates; that is a stray row, and the title now says so outright
     * ({@code "Unattached domain model #14667 (empty)"}) instead of the {@code "//TODO"} it used to
     * show.  A real model reads
     * {@code "Pet Shop Use Cases #653 (classes: 971, attributes: 109, responsibilities: 191,
     * collaborators: 237)"}, so two models can be told apart in a list without opening either.</p>
     *
     * <p>Counts are labelled rather than written as bare numbers because there is no plural to get
     * right: {@code "1 classes"} reads badly in a UI, {@code "classes: 1"} does not.</p>
     */
    @Title
    public String title() {
        final StringBuilder title = new StringBuilder(ownerTitle());
        if (getId() != null) {
            title.append(" #").append(getId());
        }
        title.append(" (");
        if (candidateCount() == 0) {
            title.append("empty");
        } else {
            title.append("classes: ").append(sizeOf(classList))
                    .append(", attributes: ").append(sizeOf(attributeList))
                    .append(", responsibilities: ").append(sizeOf(responsibilityList))
                    .append(", collaborators: ").append(sizeOf(collaboratorList));
        }
        return title.append(')').toString();
    }

    /** The owning corpus' title, or the most accurate placeholder available. */
    private String ownerTitle() {
        if (corpora == null) {
            // not injected — a plain unit test, or a path outside Causeway's entity injection
            return "Domain model";
        }
        final String ownerTitle = corpora.titleOf(this);
        return ownerTitle != null ? ownerTitle : "Unattached domain model";
    }

    /** Total candidates across all four kinds — the model's size, and whether it is empty. */
    private int candidateCount() {
        return sizeOf(classList) + sizeOf(attributeList)
                + sizeOf(responsibilityList) + sizeOf(collaboratorList);
    }

    /** {@code title()} must not fail on a half-built model, so a null collection counts as none. */
    private static int sizeOf(final List<?> candidates) {
        return candidates != null ? candidates.size() : 0;
    }

    /**
     * The classes this analysis run produced.
     * <p>
     * Public field only — <b>no</b> Lombok {@code @Getter}: a public field plus a getter of the same
     * name is two JAXB properties, and the RestfulObjects list endpoint serialises this entity, so
     * the duplicate fails the whole request with HTTP 500 ({@code Class has two properties of the
     * same name "classList"}). Callers read {@code domainModel.classList} directly. The same rule
     * applies to the three collections below.
     */
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<ClassCdd> classList = new ArrayList<>();

    /**
     * Actions owned by this analysis run, including orphan actions with no
     * owning {@link ClassCdd}.  Cascading from the {@link DomainModel} guarantees
     * they are removed when the analysis (and its {@code Document}) is deleted.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<ResponsibilityCdd> responsibilityList = new ArrayList<>();

    /**
     * Properties owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<AttributeCdd> attributeList = new ArrayList<>();

    /**
     * Associations owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<CollaboratorCdd> collaboratorList = new ArrayList<>();

    // region > owner (for the title)

    /**
     * Injected {@link Corpora} service, used to name the corpus that owns this model.
     * <p>
     * Hidden from the metamodel: an injected collaborator is <em>not</em> a property, and exposing it
     * would trip the metamodel veto (see {@code CODING_STANDARDS.md} §1).
     */
    @Transient
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Corpora corpora;

    // endregion

    // region > diagram

    @Transient
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private DomainModelDiagramsD3 domainModelDiagramsServiceD3;

    @D3Graph
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(named = "Dependency Diagram / D3", fieldSetId = "content", sequence = "1")
    public String getDiagram() {
        final long id = getId() != null ? getId() : -1L;
        if (domainModelDiagramsServiceD3 == null) {
            log.warn("DomainModel #{}: domainModelDiagramsServiceD3 is NOT injected — returning empty diagram", id);
            return "{\"nodes\":[],\"links\":[]}";
        }
        log.debug("DomainModel #{}: classList={}, responsibilityList={}, attributeList={}, collaboratorList={}, hopDepth={}",
                id,
                classList != null ? classList.size() : -1,
                responsibilityList != null ? responsibilityList.size() : -1,
                attributeList != null ? attributeList.size() : -1,
                collaboratorList != null ? collaboratorList.size() : -1,
                1);
        String result = domainModelDiagramsServiceD3.renderDiagram(this);
        if (result == null) {
            log.warn("DomainModel #{}: domainModelDiagramsServiceD3.renderDiagram() returned null — no diagram produced", id);
        } else {
            log.debug("DomainModel #{}: diagram produced ({} chars)", id, result.length());
        }
        return result != null ? result : "{\"nodes\":[],\"links\":[]}";
    }

    // endregion

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }
}
