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
     * ordered by rule-match count descending (higher = more important).
     *
     * @return the next {@link Candidate} to review, or {@code null} if none remain
     */
    @Programmatic
    public Candidate nextUnprocessed() {
        Set<Long> processedIds = Set.copyOf(reviewRepository.findProcessedCandidateIds());

        // Gather all concrete Candidate subclass instances
        List<Candidate> allCandidates = new ArrayList<>();
        allCandidates.addAll(repositoryService.allInstances(ClassCdd.class));
        allCandidates.addAll(repositoryService.allInstances(PropertyCdd.class));
        allCandidates.addAll(repositoryService.allInstances(ActionCdd.class));
        allCandidates.addAll(repositoryService.allInstances(AssociationCdd.class));
        allCandidates.addAll(repositoryService.allInstances(PackageCdd.class));
        allCandidates.addAll(repositoryService.allInstances(ParameterCdd.class));

        return allCandidates.stream()
                .filter(c -> !processedIds.contains(c.getId()))     // not yet reviewed
                .max(Comparator.comparingInt(Candidate::getRuleMatchCount) // highest match count first
                        .thenComparingLong(Candidate::getId)) // tie-break by ID
                .orElse(null);
    }
}