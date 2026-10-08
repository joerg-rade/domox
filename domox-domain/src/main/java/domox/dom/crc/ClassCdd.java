package domox.dom.crc;

import domox.DomainModule;
import generate.PumlCode;
import jakarta.inject.Named;
import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "ClassCdd")
@Named(DomainModule.NAMESPACE + ".ClassCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@NoArgsConstructor
public class ClassCdd
        extends Candidate
        implements Comparable<ClassCdd> {

    public ClassCdd(
            String name,
            List<PropertyCdd> propertyList,
            List<ActionCdd> actionList,
            List<AssociationCdd> associationList) {
        this.setCandidateName(name);
        this.propertyList = propertyList;
        this.actionList = actionList;
        this.associationList = associationList;
    }

    @Property
    @JoinColumn(nullable = false)
    @ManyToOne()
    public DomainModel domainModel;

    @Getter
    @Setter
    @Programmatic
    public ClassType classType = ClassType.PARTY_PLACE_THING;

    @Getter
    @Setter
    @Programmatic
    public String packageName = "sample";

    /**
     * The subdomain (business / functional area) this class candidate belongs to.
     * <p>
     * Set by the reviewer during the review workflow: as a {@code @ManyToOne} to the managed
     * {@link SubDomain} reference, Causeway renders it as an inline-editable dropdown on the
     * candidate's object page ({@code ClassCdd} opts into {@code editing = Editing.ENABLED}),
     * letting the reviewer pick from the existing subdomains.
     */
    @Property
    @ManyToOne
    @JoinColumn(name = "subdomain_id")
    @Getter
    @Setter
    @PropertyLayout(
            named = "Sub-domain",
            describedAs = "Sub-domain this class candidate belongs to (set during review)",
            sequence = "2.1")
    private SubDomain subDomain;

    @Getter
    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<PropertyCdd> propertyList = new ArrayList<>();

    @Getter
    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<ActionCdd> actionList = new ArrayList<>();

    @Getter
    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<AssociationCdd> associationList = new ArrayList<>();

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    @Programmatic
    public void addAction(ActionCdd action) {
        actionList.add(action);
    }

    @Programmatic
    public void addAssociation(AssociationCdd association) {
        associationList.add(association);
        association.setClassCdd(this);
    }

    @Programmatic
    public String toPlantUmlString() {
        PumlCode code = new PumlCode();
        code.addClass(getCandidateName());
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
