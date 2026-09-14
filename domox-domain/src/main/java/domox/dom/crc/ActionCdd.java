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
import lombok.ToString;
import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Publishing;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "ActionCdd")
@Named(DomainModule.NAMESPACE + ".ActionCdd")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
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
    @JoinColumn(nullable = false)
    @ManyToOne
    private ClassCdd classCdd;

    @OneToMany(mappedBy = "actionCdd", cascade = CascadeType.PERSIST)
    @Collection
    public List<ParameterCdd> inputTypeList;

    @Property
    @Column(nullable = false)
    private String outputType;

    @Override
    public int compareTo(@NotNull ActionCdd o) {
        return 0; //FIXME
    }

    public String toPlantUmlString() {
        final String sep = ", ";
        String s = getCandidateName() + "(";
        for (ParameterCdd i : inputTypeList) {
            s += i.toPlantUmlString() + sep;
        }
        s = s.replaceAll(sep + "$", ")");
        return s + ": " + outputType;
    }
}
