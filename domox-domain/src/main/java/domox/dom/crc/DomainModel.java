package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.extensions.pdfjs.applib.annotations.PdfJsViewer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "DomainModel")
@Named(DomainModule.NAMESPACE + ".DomainModel")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A DM. ...")
@NoArgsConstructor
@Data
public class DomainModel extends AbstractEntity implements Comparable<ClassCdd> {

    @Title
    public String title() {
        return "//TODO";
    }

    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    public List<ClassCdd> classList = new ArrayList<>();

    /**
     * Actions owned by this analysis run, including orphan actions with no
     * owning {@link ClassCdd}.  Cascading from the {@link DomainModel} guarantees
     * they are removed when the analysis (and its {@code Document}) is deleted.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    public List<ActionCdd> actionList = new ArrayList<>();

    /**
     * Properties owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    public List<PropertyCdd> propertyList = new ArrayList<>();

    /**
     * Associations owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    public List<AssociationCdd> associationList = new ArrayList<>();

    // region > diagram

    /**
     * Injected {@link DomainModelDiagrams} service used to render the model-wide lexical diagram
     * on demand.
     * <p>
     * The diagram is deliberately <em>not</em> persisted: it is regenerated on every access
     * because its content depends on the current candidate set, which changes as candidates are
     * reviewed, approved, or rejected.
     * <p>
     * Accessors are suppressed ({@code @Getter(AccessLevel.NONE)} etc.) so Lombok {@code @Data}
     * does not expose this managed-bean service as a Causeway property — an entity member whose
     * element-type is a {@code @DomainService} is vetoed by the metamodel.
     */
    @Transient
    @Inject
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private DomainModelDiagrams domainModelDiagramsService;

    /**
     * A freshly generated PDF of the lexical dependency graph seeded from this analysis run's
     * approved candidates, expanded {@link #getHopDepth()} hops deep.  The diagram is derived on
     * every access and never persisted, so it always reflects the current candidate set.
     */
    @PdfJsViewer
    @Property(optionality = Optionality.OPTIONAL)
    @PropertyLayout(named = "Dependency Diagram", fieldSetId = "content", sequence = "2")
    public Blob getDiagram() {
        return domainModelDiagramsService != null ? domainModelDiagramsService.renderDiagram(this) : null;
    }

    /**
     * How many hops the model-wide dependency diagram reaches out from the approved candidates:
     * {@code 1} (the default) draws the approved candidates plus every candidate directly connected
     * to them.  Larger values deepen the neighbourhood — e.g. {@code 2} additionally draws every
     * candidate connected to those direct neighbours.
     * <p>
     * Changing this value triggers the {@link #getDiagram() dependency diagram} to be rebuilt at
     * the new depth: the diagram is derived again on every access, so the next render reflects the
     * updated hop depth.
     */
    @Getter
    @Column(nullable = false)
    @PropertyLayout(
            named = "Diagram Hop Depth",
            describedAs = "How far the dependency diagram reaches out (in hops) from the approved candidates",
            fieldSetId = "content",
            sequence = "1")
    private int hopDepth = 1;

    /**
     * Sets the hop depth, clamped to a minimum of {@code 1}.  Because {@link #getDiagram()} is
     * regenerated on every access, an actual change causes the dependency diagram to be rebuilt at
     * the new depth on its next render.
     */
    public void setHopDepth(final int hopDepth) {
        this.hopDepth = Math.max(1, hopDepth);
    }

    // endregion

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }
}
