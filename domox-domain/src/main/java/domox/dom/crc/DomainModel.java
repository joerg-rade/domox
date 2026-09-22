package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.causeway.applib.annotation.*;
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

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }
}
