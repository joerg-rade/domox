package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.extensions.d3.applib.annotations.D3Graph;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "DomainModel")
@Named(DomainModule.NAMESPACE + ".DomainModel")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A DM. ...")
@NoArgsConstructor
public class DomainModel extends AbstractEntity implements Comparable<ClassCdd> {

    private static final Logger log = LoggerFactory.getLogger(DomainModel.class);

    @Title
    public String title() {
        return "//TODO";
    }

    @Getter
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
    @Getter
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<ActionCdd> actionList = new ArrayList<>();

    /**
     * Properties owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<PropertyCdd> propertyList = new ArrayList<>();

    /**
     * Associations owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<AssociationCdd> associationList = new ArrayList<>();

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
    @PropertyLayout(named = "Dependency Diagram / D3", fieldSetId = "content", sequence = "2.1")
    public String getDiagram() {
        final long id = getId() != null ? getId() : -1L;
        if (domainModelDiagramsServiceD3 == null) {
            log.warn("DomainModel #{}: domainModelDiagramsServiceD3 is NOT injected — returning empty diagram", id);
            return "{\"nodes\":[],\"links\":[]}";
        }
        log.debug("DomainModel #{}: classList={}, actionList={}, propertyList={}, associationList={}, hopDepth={}",
                id,
                classList != null ? classList.size() : -1,
                actionList != null ? actionList.size() : -1,
                propertyList != null ? propertyList.size() : -1,
                associationList != null ? associationList.size() : -1,
                hopDepth);
        String result = domainModelDiagramsServiceD3.renderDiagram(this);
        if (result == null) {
            log.warn("DomainModel #{}: domainModelDiagramsServiceD3.renderDiagram() returned null — no diagram produced", id);
        } else {
            log.debug("DomainModel #{}: diagram produced ({} chars)", id, result.length());
        }
        return result != null ? result : "{\"nodes\":[],\"links\":[]}";
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
