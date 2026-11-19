package domox.dom.crc.model;
import domox.DomainModule;
import generate.PumlCode;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
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
            List<AttributeCdd> attributeList,
            List<ResponsibilityCdd> responsibilityList,
            List<CollaboratorCdd> collaboratorList) {
        this.setCandidateName(name);
        this.attributeList = attributeList;
        this.responsibilityList = responsibilityList;
        this.collaboratorList = collaboratorList;
    }

    @Property
    @JoinColumn(nullable = false)
    @ManyToOne()
    public DomainModel domainModel;

    /**
     * The Coad archetype this class was classified as.
     * <p>
     * A public field is the property — Causeway maps it directly. It must <b>not</b> also carry a
     * Lombok {@code @Getter}: a public field plus a getter of the same name is two JAXB properties,
     * and Causeway serialises a list of domain objects with JAXB, so the duplicate makes every
     * RestfulObjects list endpoint fail with HTTP 500
     * ({@code Class has two properties of the same name "classType"}). {@code @Setter} is kept — it
     * is genuinely called by {@code CorpusIngestion}.
     */
    @Setter
    @Programmatic
    public ClassType classType = ClassType.PARTY_PLACE_THING;

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

    /**
     * The attributes extracted for this class.
     * <p>
     * Public field only — <b>no</b> Lombok {@code @Getter} (see {@link #classType}): the pair is a
     * JAXB duplicate property and breaks the RestfulObjects list endpoints. Callers read
     * {@code classCdd.attributeList} directly. Same applies to {@link #responsibilityList} and
     * {@link #collaboratorList}.
     */
    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<AttributeCdd> attributeList = new ArrayList<>();

    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<ResponsibilityCdd> responsibilityList = new ArrayList<>();

    @Setter
    @Collection
    @OneToMany(mappedBy = "classCdd", cascade = CascadeType.ALL)
    public List<CollaboratorCdd> collaboratorList = new ArrayList<>();

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return Long.compare(this.getId(), o.getId());
    }

    @Programmatic
    public void addResponsibility(ResponsibilityCdd action) {
        responsibilityList.add(action);
    }

    @Programmatic
    public void addCollaborator(CollaboratorCdd association) {
        collaboratorList.add(association);
        association.setClassCdd(this);
    }

    @Programmatic
    public String toPlantUmlString() {
        PumlCode code = new PumlCode();
        code.addClass(getCandidateName());
        code.add(" #" + classType.colorCode);
        code.addBegin();
        for (AttributeCdd p : attributeList) {
            String s = p.toPlantUmlString();
            code.addProperty(s);
        }
        for (ResponsibilityCdd a : responsibilityList) {
            String s = a.toPlantUmlString();
            code.addResponsibility(s);
        }
        code.addEnd();
        return code.getCode();
    }
}
