package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import domox.dom.rules.RuleMatch;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all UML class-diagram candidate entities.
 * <p>
 * Holds the sentence / typed-dependency provenance of a candidate, and passes on
 * the {@link AbstractEntity} {@code id}/{@code version} mapping to its concrete
 * {@code @Entity} subclasses via {@link InheritanceType#TABLE_PER_CLASS}.
 * <p>
 * Because {@code Candidate} is itself an {@code @Entity} (for relationship support
 * with {@link Review}), concrete subclasses produce their own full table via
 * TABLE_PER_CLASS inheritance — no {@code CANDIDATE} base table is created.
 */
@Entity
@Table(schema = DomainModule.SCHEMA, name = "Candidate")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Candidate")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Candidate extends AbstractEntity {

    @Column(nullable = false)
    @Getter
    @Setter
    @PropertyLayout(sequence = "1")
    private String candidateName;

    @Column(nullable = false)
    @Getter
    @Setter
    @PropertyLayout(sequence = "2")
    private String candidateType;

    @ManyToMany
    @JoinTable(schema = DomainModule.SCHEMA)
    @Getter
    @Setter
    private List<RuleMatch> ruleMatches = new ArrayList<>();

    @Inject
    @Transient
    private Reviews reviewsService;


    @PropertyLayout(sequence = "3")
    public int getRuleMatchCount() {
        return ruleMatches.size();
    }

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    @Setter
    private List<Review> reviews = new ArrayList<>();

    @PropertyLayout(sequence = "4")
    public int getReviewCount() {
        return reviews.size();
    }

    @PropertyLayout(sequence = "5")
    public int getApprovalCount() {
        final List<Review> approvals = reviews.stream()
                .filter(r -> r.getStatus() == ReviewStatus.APPROVED)
                .toList();
        return approvals.size();
    }

    /**
     * Adds a matching rule to this candidate.
     *
     * @param match The rule match to add.
     */
    @Programmatic
    public void addMatchingRule(RuleMatch match) {
        if (match != null && !ruleMatches.contains(match)) {
            ruleMatches.add(match);
        }
    }

    @Programmatic
    public void addReview(Review review) {
        if (review != null && !reviews.contains(review)) {
            reviews.add(review);
            review.setCandidate(this);
        }
    }

    @Programmatic
    public void removeReview(Review review) {
        if (review != null && reviews.remove(review)) {
            review.setCandidate(null);
        }
    }

    @Action
    @ActionLayout(
            sequence = "6",
            position = ActionLayout.Position.PANEL,
            describedAs = "Create a new review for this candidate")
    public Review createReview() {
        return reviewsService.create(this);
    }
}