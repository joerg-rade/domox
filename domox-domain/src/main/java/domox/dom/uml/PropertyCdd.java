package domox.dom.uml;

import domox.DomainModule;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".PropertyCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Property is a Member of a Class")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class PropertyCdd
        extends Candidate
        implements Comparable<PropertyCdd> {
    private Cardinality cardinality;

    public PropertyCdd(String propertyName, String type) {
        this.name = propertyName;
        this.type = type;
    }

    public PropertyCdd(Sentence sentence, TypedDependency dependency) {
        setSentence(sentence);
        addTypedDependency(dependency);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Column(name = "name", nullable = false)
    @Getter
    @Setter
    public String name;

    @Property
    @JoinColumn(nullable = false) // this always points to the owning class
    @ManyToOne
    public ClassCdd classCdd;

    @Setter
    @Property
    @Column(nullable = false)
    /**
     * Field to store the property type (e.g., "int", "String")
     * But not only primitives - class candidates from the scope of this analysis are to be set here as well
     */
    public String type;

    @Override
    public int compareTo(@NotNull PropertyCdd o) {
        //FIXME
        return 0;
    }

    public String toPlantUmlString() {
        return name + ": " + type;
    }
}
