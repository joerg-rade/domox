package domox.dom.crc;

import domox.dom.AbstractEntity;
import domox.dom.rules.RuleMatch;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.Programmatic;
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
@MappedSuperclass
@EntityListeners(CausewayEntityListener.class)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Candidate extends AbstractEntity {

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateName;

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateType;

    @Transient
    @Getter
    @Setter
    private List<RuleMatch> ruleMatches;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    @Setter
    private List<Review> reviews = new ArrayList<>();

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
}