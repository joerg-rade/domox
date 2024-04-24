package domox.dom.uml;

import diagram.PumlCode;
import domox.DomainModule;
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
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

enum ClassType {
    /**
     * Does it represent a moment or interval of time
     * that we need to remember and work with for legal or business reasons?
     * Examples in business systems generally model activities involving
     * people, places and things such as
     * a sale, an order, a rental, an employment, making a journey, etc.
     */
    MOMENT_INTERVAL("pink", "pink"),
    /**
     * Is it a way of participating in an activity (by either a person,place,or thing)?
     * A person playing the role of:
     * an employee in an employment,
     * a thing playing the role of a product in a sale,
     * a location playing the role of a classroom for a training course,
     * are examples of roles.+
     */
    ROLE("yellow", "gold"),
    /**
     * Is it simply a catalog-entry-like description which classifies or 'labels' an object?
     * For example, the make and model of a car categorises or describes a number of physical vehicles.
     * The relationship between the blue description and green party, place or thing
     * is a type-instance relationship based on differences in the values of data items held in the 'type' object.+
     */
    DESCRIPTION("blue", "lightblue"),
    /**
     * Something tangible, uniquely identifiable. Typically the role-players in a system.
     * People are green. Organizations are green. The physical objects involved in a rental such as the physical DVDs are green.
     * Normally, if you get through the above three questions and end up here, your class is a "green."
     */
    PARTY_PLACE_THING("green", "lightgreen");

    final String colorName;
    final String colorCode;

    ClassType(String colorName, String colorCode) {
        this.colorName = colorName;
        this.colorCode = colorCode;
    }
}

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

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<PropertyCdd> propertyList = new ArrayList<>();

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<ActionCdd> actionList = new ArrayList<>();

    @Property
    @OneToMany(mappedBy = "classCdd")
    public List<AssociationCdd> associationList = new ArrayList<>();

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }

    public void addAction(ActionCdd action) {
        actionList.add(action);
    }

    public void addAssociation(AssociationCdd association) {
        associationList.add(association);
    }

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
