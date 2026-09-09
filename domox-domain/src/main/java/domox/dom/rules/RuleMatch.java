package domox.dom.rules;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import domox.dom.nlp.TypedDependency;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.jspecify.annotations.NonNull;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "RuleMatch")
@Named(DomainModule.NAMESPACE + ".RuleMatch")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "thumbs-up",
        tableDecorator = TableDecorator.DatatablesNet.class,
        bookmarking = BookmarkPolicy.AS_ROOT)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class RuleMatch extends AbstractEntity implements Comparable<RuleMatch> {

    @Title
    public String title() {
        return this.ruleClassName;
    }

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
