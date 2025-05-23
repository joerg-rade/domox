package domox.dom.uml;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".ActionCdd")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "bolt")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class ActionCdd
        extends Candidate
        implements Comparable<ActionCdd> {

    public ActionCdd(String name, List<ParameterCdd> inputTypeList, String outputType) {
        this.name = name;
        this.inputTypeList = inputTypeList;
        this.outputType = outputType;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Property
    @JoinColumn(nullable = false)
    @ManyToOne
    private ClassCdd classCdd;

    @OneToMany(mappedBy = "actionCdd", cascade = CascadeType.PERSIST)
    @Property
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
        String s = name + "(";
        for (ParameterCdd i : inputTypeList) {
            s += i.toPlantUmlString() + sep;
        }
        s = s.replaceAll(sep + "$", ")");
        return s + ": " + outputType;
    }
}
