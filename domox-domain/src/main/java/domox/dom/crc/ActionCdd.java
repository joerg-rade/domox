package domox.dom.crc;

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

import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "ActionCdd")
@Named(DomainModule.NAMESPACE + ".ActionCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "bolt")
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class ActionCdd
        extends Candidate
        implements Comparable<ActionCdd> {

    public ActionCdd(String name, List<ParameterCdd> inputTypeList, String outputType) {
        this.setCandidateName(name);
        this.inputTypeList = inputTypeList;
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

    @OneToMany(mappedBy = "actionCdd", cascade = CascadeType.ALL)
    @Collection
    public List<ParameterCdd> inputTypeList;

    @Property
    @Column(nullable = false)
    @Setter
    private String outputType;

    @Override
    public int compareTo(@NotNull ActionCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    public String toPlantUmlString() {
        final String sep = ", ";
        StringBuilder s = new StringBuilder(getCandidateName() + "(");
        for (ParameterCdd i : inputTypeList) {
            s.append(i.toPlantUmlString()).append(sep);
        }
        s = new StringBuilder(s.toString().replaceAll(sep + "$", ")"));
        return s + ": " + outputType;
    }
}
