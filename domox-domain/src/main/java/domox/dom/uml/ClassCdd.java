package domox.dom.uml;

import domox.DomainModule;
import generate.PumlCode;
import jakarta.inject.Named;
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
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".ClassCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor
public class ClassCdd
        extends Candidate
        implements Comparable<ClassCdd> {

    public ClassCdd(
            String name,
            List<PropertyCdd> propertyList,
            List<ActionCdd> actionList,
            List<AssociationCdd> associationList) {
        this.name = name;
        this.propertyList = propertyList;
        this.actionList = actionList;
        this.associationList = associationList;
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
    @JoinColumn
    @ManyToOne()
    public DomainModel domainModel;

    @Setter
    @Property
    public ClassType classType = ClassType.PARTY_PLACE_THING;

    @Setter
    @Property
    public String packageName = "sample";

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<PropertyCdd> propertyList;

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<ActionCdd> actionList;

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<AssociationCdd> associationList;

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }

    @Programmatic
    public void addAction(ActionCdd action) {
        actionList.add(action);
    }

    @Programmatic
    public void addAssociation(AssociationCdd association) {
        associationList.add(association);
    }

    @Programmatic
    public String toPlantUmlString() {
        PumlCode code = new PumlCode();
        code.addClass(name);
        code.add(" #" + classType.colorCode);
        code.addBegin();
        for (PropertyCdd p : propertyList) {
            String s = p.toPlantUmlString();
            code.addProperty(s);
        }
        for (ActionCdd a : actionList) {
            String s = a.toPlantUmlString();
            code.addAction(s);
        }
        code.addEnd();
        return code.getCode();
    }
}
