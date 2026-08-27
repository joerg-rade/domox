package domox.dom.nlp;

import com.deliveredtechnologies.rulebook.NameValueReferable;
import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.io.Serializable;
import java.util.Arrays;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".TypedDependency")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "scan")
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
    private TdType type;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "sentence_id")
    @Getter
    @Setter
    private Sentence sentence;

    @Column(nullable = false)
    @Getter
    @Setter
    @Programmatic
    private int governorIndex;

    @Column(nullable = false)
    @Getter
    @Setter
    @Programmatic
    private int dependentIndex;

    @Column(length = 255)
    @Getter
    @Setter
    @Programmatic
    private String governorGloss;      // token A text

    @Column(length = 255)
    @Getter
    @Setter
    @Programmatic
    private String dependentGloss;     // token B text

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

    @Programmatic
    public String getPartA() {
        return governorGloss;
    }

    @Programmatic
    public String getPartB() {
        return dependentGloss;
    }

    public boolean isVerbA() {
        return governorPos != null && Arrays.asList(VERB_TYPES).contains(governorPos);
    }

    public boolean isNounB() {
        return dependentPos != null && Arrays.asList(NOUN_TYPES).contains(dependentPos);
    }

    public String getA() {
        return governorGloss;
    }

    public String getB() {
        return dependentGloss;
    }

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "relation_id")
    private Relation relation;

    //region > compareTo, toString
    @Override
    public int compareTo(final TypedDependency other) {
        return Long.compare(this.id, other.id);
    }
    //endregion

    public boolean nsubj() {
        return getType().equals(TdType.NSUBJ);
    }

    public boolean nsubjpass() {
        return getType().equals(TdType.NSUBJ);
    }

    public boolean compound() {
        return getType().equals(TdType.COMPOUND);
    }

    private static final PartOfSpeechType[] VERB_TYPES = {
            PartOfSpeechType.VB,
            PartOfSpeechType.VBG,
            PartOfSpeechType.VBN,
            PartOfSpeechType.VBP,
            PartOfSpeechType.VBZ};


    private static final PartOfSpeechType[] NOUN_TYPES = {
            PartOfSpeechType.NN,
            PartOfSpeechType.NNP,
            PartOfSpeechType.NNS,
            PartOfSpeechType.NFP};

    private static final String[] BASIC_ATTRIB = {"name", "number", "type", "address", "level", "date", "time"};

    @Programmatic
    public boolean isBasicAttributeB() {
        final String bName = getPartB();
        if (bName == null) return false;
        return Arrays.asList(BASIC_ATTRIB).contains(bName);
    }

    @Programmatic
    public boolean isBasicAttributeA() {
        final String aName = getPartA();
        if (aName == null) return false;
        return Arrays.asList(BASIC_ATTRIB).contains(aName);
    }

    @Programmatic
    public boolean isNounA() {
        return governorPos != null && Arrays.asList(NOUN_TYPES).contains(governorPos);
    }

    private static final PartOfSpeechType[] ADJECTIVE_TYPES = {
            PartOfSpeechType.JJ};

    @Programmatic
    public boolean isAdjectiveB() {
        return dependentPos != null && Arrays.asList(ADJECTIVE_TYPES).contains(dependentPos);
    }

    public boolean dobj() {
        // Direct object: in Stanford UD, this is 'obj'
        return getType().equals(TdType.OBJ);
    }

    public boolean iobj() {
        // Indirect object: check for indirect object patterns in obl types
        return getType().equals(TdType.OBL);
    }

    public boolean pobj() {
        // Prepositional object: check for obl types
        return getType().toString().startsWith("OBL");
    }

    public boolean amod() {
        return getType().equals(TdType.AMOD);
    }

    public boolean advmod() {
        return getType().equals(TdType.ADVMOD);
    }

    public boolean nmodOf() {
        return getType().equals(TdType.NMOD_OF);
    }

    public boolean nmodIn() {
        // in: use obl:in or similar
        return getType().equals(TdType.OBL_IN) || getType().equals(TdType.NMOD);
    }

    public boolean nmodTo() {
        // to: use obl:to or similar
        return getType().equals(TdType.OBL_TO);
    }

    public boolean nmodFor() {
        return getType().equals(TdType.NMOD_FOR) || getType().equals(TdType.OBL_FOR);
    }

    public boolean nmodFrom() {
        // from: check for obl types
        return getType().toString().contains("FROM") || getType().toString().contains("from");
    }

    public boolean nmodAs() {
        // as: check obl types or nmod
        return getType().toString().contains("AS") || getType().toString().contains("as");
    }

    public boolean nmodBy() {
        return getType().equals(TdType.OBL_BY) || getType().equals(TdType.NMOD);
    }

    public boolean nmodAgent() {
        // agent: typically obl:agent or nmod:agent, might not exist exactly
        return getType().toString().contains("AGENT") || getType().toString().contains("agent");
    }

    public boolean nmodWith() {
        return getType().equals(TdType.NMOD_WITH) || getType().equals(TdType.OBL_WIN);
    }

    public boolean nmodPoss() {
        return getType().equals(TdType.NMOD_POSS);
    }

    public boolean nmodAnd() {
        return getType().equals(TdType.CONJ_AND);
    }

    public boolean nmodOr() {
        return getType().equals(TdType.CONJ_OR);
    }

    public boolean mark() {
        return getType().equals(TdType.MARK);
    }

    public boolean xcomp() {
        return getType().equals(TdType.XCOMP);
    }

    public boolean advcl() {
        return getType().equals(TdType.ADVCL);
    }

    public boolean nummod() {
        return getType().equals(TdType.NUMMOD);
    }

    public boolean det() {
        return getType().equals(TdType.DET);
    }

    public boolean neg() {
        // neg doesn't exist in TdType, so check if it might be in a string or as a pattern
        return getType().toString().contains("NEG") || getType().toString().contains("neg");
    }

    // ===== NameValueReferable Implementation =====

    @Override
    public String getName() {
        return "TypedDependency_" + (id != null ? id : "new");
    }

    @Override
    public void setName(String name) {
        // setName not applicable for TypedDependency; name is auto-generated
        // This method is required by NameValueReferable but not used
    }

    @Override
    public Object getValue() {
        return this;
    }

    @Override
    public void setValue(Object value) {
        // setValue not applicable for TypedDependency; this object is immutable
        // This method is required by NameValueReferable but not used
    }
}
