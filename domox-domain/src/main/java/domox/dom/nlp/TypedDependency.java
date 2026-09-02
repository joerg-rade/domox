package domox.dom.nlp;

import com.deliveredtechnologies.rulebook.NameValueReferable;
import domox.DomainModule;
import domox.dom.rules.RuleMatch;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.io.Serializable;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".TypedDependency")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "text-width")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class TypedDependency implements Comparable<TypedDependency>, NameValueReferable, Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    @Property()
    @Getter
    @Setter
    @PropertyLayout(sequence = "3")
    private TdType type;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "sentence_id")
    @Getter
    @Setter
    @Programmatic
    private Sentence sentence;

    @Column(nullable = false)
    @Getter
    @Setter
    private int governorIndex;

    @Column(nullable = false)
    @Getter
    @Setter
    @PropertyLayout(sequence = "1")
    private int dependentIndex;

    @Column(length = 255)
    @Getter
    @Setter
    private String governorGloss;

    @Column(length = 255)
    @Getter
    @Setter
    @PropertyLayout(sequence = "2")
    private String dependentGloss;

    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    @Programmatic
    private PartOfSpeechType governorPos;

    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    @Programmatic
    private PartOfSpeechType dependentPos;

    @Column(length = 255)
    @Getter
    @Setter
    @PropertyLayout(sequence = "4")
    private String governorLemma;

    @Column(length = 255)
    @Getter
    @Setter
    @PropertyLayout(sequence = "5")
    private String dependentLemma;

    @Programmatic
    public String getA() {
        return governorLemma;
    }

    @Programmatic
    public String getB() {
        return dependentLemma;
    }

    @OneToMany(mappedBy = "typedDependency", cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "typed_dependency_id")
    @Getter
    @Setter
    private List<RuleMatch> ruleMatches;

    //region > compareTo, toString
    @Override
    public int compareTo(final TypedDependency other) {
        return Long.compare(this.id, other.id);
    }
    //endregion

    // ===== NameValueReferable Implementation =====
    @Override
    @Programmatic
    public String getName() {
        return "TypedDependency_" + (id != null ? id : "new");
    }

    @Override
    public void setName(String name) {
        // setName not applicable for TypedDependency; name is auto-generated
        // This method is required by NameValueReferable but not used
    }

    @Override
    @Programmatic
    public Object getValue() {
        return this;
    }

    @Override
    public void setValue(Object value) {
        // setValue not applicable for TypedDependency; this object is immutable
        // This method is required by NameValueReferable but not used
    }
}
