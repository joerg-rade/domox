package domox.dom.crc.model;
import domox.dom.AbstractEntity;
import domox.DomainModule;
import domox.dom.crc.diagram.DomainModelDiagramsD3;
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
    public List<ResponsibilityCdd> responsibilityList = new ArrayList<>();

    /**
     * Properties owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<AttributeCdd> attributeList = new ArrayList<>();

    /**
     * Associations owned by this analysis run.  Cascading from the
     * {@link DomainModel} guarantees they are removed with the analysis.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter
    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    @CollectionLayout(sortedBy = CandidateByRuleMatchCountComparator.class)
    public List<CollaboratorCdd> collaboratorList = new ArrayList<>();

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
