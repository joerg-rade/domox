package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import domox.dom.rules.RuleMatch;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.services.message.MessageService;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Inject
    @Transient
    private Reviews reviewsService;

    @Inject
    @Transient
    private MessageService messageService;

    @Title
    public String title() {
        return getClass().getSimpleName() + ": " + candidateName;
    }

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
    @CollectionLayout(sequence = "1")
    private List<RuleMatch> ruleMatches = new ArrayList<>();

    @PropertyLayout(sequence = "3")
    public int getRuleMatchCount() {
        return ruleMatches.size();
    }

    @PropertyLayout(sequence = "4")
    public String getRuleNames() {
        return ruleMatches.stream()
                .map(RuleMatch::getRuleClassName)
                .filter(name -> name != null)
                .collect(Collectors.joining(", "));
    }

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    @Setter
    @CollectionLayout(sequence = "2")
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
     * <p>
     * The {@code ruleMatches} list is de-duplicated by the same candidate + rule
     * signature used when {@code RuleMatches} persists matches ({@code ruleClassName},
     * {@code candidateType}, {@code candidateName}, {@code relatedCandidateType},
     * {@code relatedCandidateName}, {@code description}), so a candidate can never
     * expose two equivalent matches — regardless of candidate type (ClassCdd,
     * ActionCdd, PropertyCdd, AssociationCdd).  This also collapses duplicate M:N
     * association rows left over from runs that predate RuleMatch de-duplication.
     *
     * @param match The rule match to add.
     */
    @Programmatic
    public void addMatchingRule(RuleMatch match) {
        if (match == null) {
            return;
        }
        // First collapse any pre-existing signature-duplicates already attached to
        // this candidate, then only append if no equivalent match is present.
        deduplicateRuleMatches();
        if (hasRuleMatchWithSameSignature(match)) {
            return;
        }
        ruleMatches.add(match);
    }

    /**
     * Removes any {@code RuleMatch} entries sharing the same candidate + rule
     * signature (keeping the first occurrence), so the {@code ruleMatches} list
     * holds no duplicates.  Signature match is defined exactly as in
     * {@code RuleMatches.create(...)}.
     */
    @Programmatic
    public void deduplicateRuleMatches() {
        if (ruleMatches.size() < 2) {
            return;
        }
        final List<RuleMatch> deduped = new ArrayList<>(ruleMatches.size());
        for (final RuleMatch current : ruleMatches) {
            boolean duplicate = false;
            for (final RuleMatch kept : deduped) {
                if (sameRuleMatchSignature(current, kept)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(current);
            }
        }
        if (deduped.size() != ruleMatches.size()) {
            ruleMatches.clear();
            ruleMatches.addAll(deduped);
        }
    }

    /**
     * Returns whether any {@code RuleMatch} already attached to this candidate has
     * the same candidate + rule signature as {@code match}.
     */
    private boolean hasRuleMatchWithSameSignature(RuleMatch match) {
        for (final RuleMatch existing : ruleMatches) {
            if (sameRuleMatchSignature(existing, match)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two {@code RuleMatch} records are considered equivalent when all of
     * {@code ruleClassName}, {@code candidateType}, {@code candidateName},
     * {@code relatedCandidateType}, {@code relatedCandidateName} and
     * {@code description} match — the same signature used by
     * {@code RuleMatches.create(...)} to avoid persisting duplicates.
     */
    private boolean sameRuleMatchSignature(RuleMatch a, RuleMatch b) {
        return a != null
                && b != null
                && Objects.equals(a.getRuleClassName(), b.getRuleClassName())
                && Objects.equals(a.getCandidateType(), b.getCandidateType())
                && Objects.equals(a.getCandidateName(), b.getCandidateName())
                && Objects.equals(a.getRelatedCandidateType(), b.getRelatedCandidateType())
                && Objects.equals(a.getRelatedCandidateName(), b.getRelatedCandidateName())
                && Objects.equals(a.getDescription(), b.getDescription());
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

    // --- Review workflow actions ---

    @Action(
            semantics = SemanticsOf.NON_IDEMPOTENT,
            commandPublishing = Publishing.ENABLED)
    @ActionLayout(
            sequence = "7",
            position = ActionLayout.Position.PANEL,
            cssClassFa = "check-circle",
            describedAs = "Approve this candidate and go to the next unprocessed one")
    public Candidate approveAndGoToNext() {
        Review review = findOrCreatePendingReview();
        reviewsService.approve(review);
        Candidate next = reviewsService.nextUnprocessed();
        if (next == null) {
            messageService.informUser("All candidates have been reviewed. Nothing left to review.");
        }
        return next; // Causeway will navigate to the returned entity (or stay if null)
    }

    @Action(
            semantics = SemanticsOf.NON_IDEMPOTENT,
            commandPublishing = Publishing.ENABLED)
    @ActionLayout(
            sequence = "8",
            position = ActionLayout.Position.PANEL,
            cssClassFa = "times-circle",
            describedAs = "Reject this candidate with a rationale and go to the next unprocessed one")
    public Candidate rejectAndGoToNext(final ReviewRationale rationale) {
        Review review = findOrCreatePendingReview();
        reviewsService.reject(review, rationale);
        Candidate next = reviewsService.nextUnprocessed();
        if (next == null) {
            messageService.informUser("All candidates have been reviewed. Nothing left to review.");
        }
        return next;
    }

    @MemberSupport
    public List<ReviewRationale> choices0RejectAndGoToNext() {
        return List.of(ReviewRationale.values());
    }

    /**
     * Returns an existing pending review for this candidate, or creates a new one.
     */
    @Programmatic
    public Review findOrCreatePendingReview() {
        return reviews.stream()
                .filter(r -> r.getStatus() == null)
                .findFirst()
                .orElseGet(() -> reviewsService.create(this));
    }
}