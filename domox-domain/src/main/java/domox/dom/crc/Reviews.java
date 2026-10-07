package domox.dom.crc;

import domox.DomainModule;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.user.UserService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@DomainService
@Named(DomainModule.NAMESPACE + ".Reviews")
@Priority(PriorityPrecedence.EARLY)
public class Reviews {

    /**
     * Username attributed to reviews created by the automated LLM/MCP reviewer
     * (replacing the human-in-the-loop reviewer).
     */
    public static final String AGENT_USER = "agent";

    private static final List<Class<? extends Candidate>> CANDIDATE_SUBCLASSES = Candidate.SUBCLASSES;

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ReviewRepository reviewRepository;
    private final UserService userService;

    @Inject
    public Reviews(
            RepositoryService repositoryService,
            FactoryService factoryService,
            ReviewRepository reviewRepository,
            UserService userService) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.reviewRepository = reviewRepository;
        this.userService = userService;
    }

    @ActionLayout(sequence = "1")
    public List<Review> listAll() {
        return repositoryService.allInstances(Review.class);
    }

    @ActionLayout(sequence = "2")
    public List<Review> findByCandidate(Candidate candidate) {
        return reviewRepository.findByCandidateId(candidate.getId());
    }

    // provide choices for the 'candidate' parameter
    @MemberSupport
    public List<Candidate> choices0FindByCandidate() {
        return repositoryService.allInstances(Candidate.class);
    }

    @Programmatic
    public Review create(Candidate candidate) {
        final Review review = factoryService.detachedEntity(Review.class);
        review.setCandidate(candidate);
        review.setUser(userService.currentUserNameElseNobody());
        review.setCreatedAt(Timestamp.from(Instant.now()));
        if (candidate != null) {
            candidate.getReviews().add(review);
        }
        repositoryService.persist(review);
        return review;
    }

    @Programmatic
    public Review approve(Review review) {
        review.setStatus(ReviewStatus.APPROVED);
        review.setUpdatedAt(Timestamp.from(Instant.now()));
        repositoryService.persist(review);
        return review;
    }

    @Programmatic
    public Review reject(Review review, ReviewRationale rationale) {
        review.setStatus(ReviewStatus.REJECTED);
        review.setRationale(rationale);
        review.setUpdatedAt(Timestamp.from(Instant.now()));
        repositoryService.persist(review);
        return review;
    }

    @Programmatic
    public Review resubmit(Review review) {
        review.setStatus(null);
        review.setRationale(null);
        review.setUpdatedAt(Timestamp.from(Instant.now()));
        repositoryService.persist(review);
        return review;
    }

    // --- Workflow: next-unprocessed candidate ---

    /**
     * Finds the next candidate that has not yet been reviewed (approved or rejected),
     * of any type.  Delegates to {@link #nextUnprocessedOfType(Class)} with no type
     * restriction.
     *
     * @return the next {@link Candidate} to review, or {@code null} if none remain
     */
    @Programmatic
    public Candidate nextUnprocessed() {
        return nextUnprocessedOfType(null);
    }

    /**
     * Finds the next candidate that has not yet been reviewed (approved or rejected),
     * ordered by rule-match count descending (higher = more important).
     * <p>
     * When {@code type} is non-null, only candidates of that concrete type are considered,
     * so a reviewer working through one candidate type (e.g. all {@link ClassCdd}s) never
     * jumps to a different type via the {@code approveAndGoToNext}/{@code rejectAndGoToNext}
     * actions.
     *
     * @param type the concrete {@link Candidate} type to restrict to, or {@code null} for all types
     * @return the next {@link Candidate} to review, or {@code null} if none remain
     */
    @Programmatic
    public Candidate nextUnprocessedOfType(Class<? extends Candidate> type) {
        Set<Long> processedIds = Set.copyOf(reviewRepository.findProcessedCandidateIds());

        return findAll().stream()
                .filter(c -> type == null || type.isInstance(c))     // restrict to type (or all)
                .filter(c -> !processedIds.contains(c.getId()))     // not yet reviewed
                .max(Comparator.comparingInt(Candidate::getRuleMatchCount) // highest match count first
                        .thenComparingLong(Candidate::getId)) // tie-break by ID
                .orElse(null);
    }

    /**
     * Loads a candidate by id, regardless of which concrete subclass it is.
     * <p>
     * Needed because the MCP client references candidates only by {@code id}; a direct
     * query on the {@link Candidate} base is not possible since it uses
     * {@link jakarta.persistence.InheritanceType#TABLE_PER_CLASS} (no base table exists).
     *
     * @return the candidate with the given id, or {@code null} if not found
     */
    @Programmatic
    public Candidate findByUniqueId(long candidateId) {
        return findAll().stream()
                .filter(c -> c.getId() != null && c.getId() == candidateId)
                .findFirst()
                .orElse(null);
    }

    private List<Candidate> findAll() {
        final List<Candidate> allCandidates = new ArrayList<>();
        for (final Class<? extends Candidate> type : CANDIDATE_SUBCLASSES) {
            allCandidates.addAll(repositoryService.allInstances(type));
        }
        return allCandidates;
    }

    // --- Automated (LLM/MCP) review workflow ---

    /**
     * Returns the review previously created for the automated agent for this candidate,
     * creating a fresh pending one (attributed to {@link #AGENT_USER}) if none exists yet.
     * <p>
     * Idempotent: repeated calls reuse the same review row rather than duplicating it.
     */
    @Programmatic
    public Review findOrCreateReviewForAgent(Candidate candidate) {
        return candidate.getReviews().stream()
                .filter(r -> AGENT_USER.equals(r.getUser()))
                .findFirst()
                .orElseGet(() -> createForUser(candidate, AGENT_USER));
    }

    /**
     * Approves the candidate via the automated agent, creating the review if needed.
     * <p>
     * Idempotent: approving a candidate that is already approved by the agent is a no-op.
     */
    @Programmatic
    public Review approveAsAgent(Candidate candidate) {
        return approve(findOrCreateReviewForAgent(candidate));
    }

    /**
     * Rejects the candidate via the automated agent with the given rationale,
     * creating the review if needed.
     * <p>
     * Idempotent: rejecting again merely refreshes status/rationale/timestamp.
     */
    @Programmatic
    public Review rejectAsAgent(Candidate candidate, ReviewRationale rationale) {
        return reject(findOrCreateReviewForAgent(candidate), rationale);
    }

    private Review createForUser(Candidate candidate, String user) {
        final Review review = factoryService.detachedEntity(Review.class);
        review.setCandidate(candidate);
        review.setUser(user);
        review.setCreatedAt(Timestamp.from(Instant.now()));
        if (candidate != null) {
            candidate.getReviews().add(review);
        }
        repositoryService.persist(review);
        return review;
    }
}