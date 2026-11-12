package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewRationale;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.message.MessageService;

/**
 * The <b>candidate review workflow</b>: creating a review for a candidate, approving/rejecting it and
 * moving on to the next unprocessed candidate of the same type.
 * <p>
 * This behaviour used to sit on the {@link Candidate} entity itself, which meant the entity carried
 * two injected services ({@code Reviews}, {@code MessageService}) and knew about the "all reviewed"
 * user message and the "next of the same type" navigation rule — none of which is the entity's own
 * state.  The entity keeps the {@code @Action}s (Causeway needs them there, and they are what the
 * viewer renders) but they are now one-line delegations to this service.
 * <p>
 * Kept separate from {@link Reviews}, which owns the {@link Review} lifecycle (create/approve/reject/
 * resubmit) and the "next unprocessed candidate" query: this service only orchestrates those two into
 * the reviewer's click-through flow.
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".CandidateReviewWorkflow")
@Priority(PriorityPrecedence.EARLY)
public class CandidateReviewWorkflow {

    /** The message shown when a reviewer reaches the end of a candidate type's queue. */
    private static final String NOTHING_LEFT = "All candidates of this type have been reviewed. Nothing left to review.";

    private final Reviews reviews;
    private final MessageService messageService;

    @Inject
    public CandidateReviewWorkflow(final Reviews reviews, final MessageService messageService) {
        this.reviews = reviews;
        this.messageService = messageService;
    }

    /** Creates a brand-new review for {@code candidate}, whatever it already carries. */
    @Programmatic
    public Review createReview(final Candidate candidate) {
        return reviews.create(candidate);
    }

    /**
     * Returns the candidate's existing pending review (one with no status yet), or creates a new one
     * when it has none.
     */
    @Programmatic
    public Review pendingOrNew(final Candidate candidate) {
        return candidate.getReviews().stream()
                .filter(r -> r.getStatus() == null)
                .findFirst()
                .orElseGet(() -> reviews.create(candidate));
    }

    /**
     * Approves {@code candidate} and returns the next unprocessed candidate <em>of the same
     * type</em> — or {@code null} (after telling the user the queue is exhausted) when none remains.
     */
    @Programmatic
    public Candidate approveAndNext(final Candidate candidate) {
        reviews.approve(pendingOrNew(candidate));
        return nextOrInform(candidate);
    }

    /**
     * Rejects {@code candidate} with {@code rationale} and returns the next unprocessed candidate
     * <em>of the same type</em> — or {@code null} (after telling the user the queue is exhausted)
     * when none remains.
     */
    @Programmatic
    public Candidate rejectAndNext(final Candidate candidate, final ReviewRationale rationale) {
        reviews.reject(pendingOrNew(candidate), rationale);
        return nextOrInform(candidate);
    }

    private Candidate nextOrInform(final Candidate candidate) {
        final Candidate next = reviews.nextUnprocessedOfType(candidate.getClass());
        if (next == null) {
            messageService.informUser(NOTHING_LEFT);
        }
        return next;
    }
}
