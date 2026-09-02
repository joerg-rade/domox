package domox.dom.rules;

import domox.DomainModule;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jspecify.annotations.NonNull;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "RuleMatch")
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".RuleMatch")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "thumbs-up",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class RuleMatch implements Comparable<RuleMatch> {

    @Title
    public String title() {
        return this.ruleClassName;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    @Getter
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "typed_dependency_id", nullable = false)
    @Getter
    @Setter
    private TypedDependency typedDependency;

    @Column(nullable = false)
    @Getter
    @Setter
    private String ruleClassName;

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateType;

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateName;

    @Column
    @Getter
    @Setter
    private String relatedCandidateType;

    @Column
    @Getter
    @Setter
    private String relatedCandidateName;

    @Column
    @Getter
    @Setter
    private String description;

    @Override
    public int compareTo(@NonNull RuleMatch o) {
        return 0;
    }
}
