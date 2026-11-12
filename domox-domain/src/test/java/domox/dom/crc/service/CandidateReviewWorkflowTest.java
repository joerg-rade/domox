package domox.dom.crc.service;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.crc.model.ReviewStatus;
import org.apache.causeway.applib.services.message.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies the candidate review workflow the {@link Candidate} entity delegates to: it creates a
 * review only when the candidate has no pending one, and the approve/reject actions move on to the
 * next unprocessed candidate of the same type — telling the user when there is none.
 */
@ExtendWith(MockitoExtension.class)
class CandidateReviewWorkflowTest {

    @Mock
    Reviews mockReviews;

    @Mock
    MessageService mockMessageService;

    private CandidateReviewWorkflow classUnderTest;

    @BeforeEach
    void setUp() {
        classUnderTest = new CandidateReviewWorkflow(mockReviews, mockMessageService);
    }

    // --- createReview ---

    @Test
    void createReview_delegatesToReviews() {
        final ClassCdd candidate = candidate();
        final Review created = new Review();
        when(mockReviews.create(candidate)).thenReturn(created);

        assertThat(classUnderTest.createReview(candidate)).isSameAs(created);
    }

    // --- pendingOrNew ---

    @Test
    void pendingOrNew_reusesAnExistingPendingReview() {
        final ClassCdd candidate = candidate();
        final Review pending = review(ReviewStatus.APPROVED);
        pending.setStatus(null);
        final Review decided = review(ReviewStatus.APPROVED);
        candidate.setReviews(new ArrayList<>(List.of(decided, pending)));

        assertThat(classUnderTest.pendingOrNew(candidate)).isSameAs(pending);
        verify(mockReviews, never()).create(any());
    }

    @Test
    void pendingOrNew_createsAReviewWhenNoneIsPending() {
        final ClassCdd candidate = candidate();
        candidate.setReviews(new ArrayList<>(List.of(review(ReviewStatus.APPROVED))));
        final Review created = new Review();
        when(mockReviews.create(candidate)).thenReturn(created);

        assertThat(classUnderTest.pendingOrNew(candidate)).isSameAs(created);
    }

    // --- approveAndNext ---

    @Test
    void approveAndNext_approvesThePendingReviewAndReturnsTheNextCandidateOfTheSameType() {
        final ClassCdd candidate = candidate();
        final Review pending = new Review();
        candidate.setReviews(new ArrayList<>(List.of(pending)));
        final ClassCdd next = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(mockReviews.nextUnprocessedOfType(ClassCdd.class)).thenReturn(next);

        assertThat(classUnderTest.approveAndNext(candidate)).isSameAs(next);
        verify(mockReviews).approve(pending);
        verify(mockMessageService, never()).informUser(any());
    }

    @Test
    void approveAndNext_informsTheUserWhenTheQueueIsExhausted() {
        final ClassCdd candidate = candidate();
        candidate.setReviews(new ArrayList<>(List.of(new Review())));
        when(mockReviews.nextUnprocessedOfType(ClassCdd.class)).thenReturn(null);

        assertThat(classUnderTest.approveAndNext(candidate)).isNull();
        verify(mockMessageService).informUser("All candidates of this type have been reviewed. Nothing left to review.");
    }

    // --- rejectAndNext ---

    @Test
    void rejectAndNext_rejectsWithTheRationaleAndReturnsTheNextCandidateOfTheSameType() {
        final ClassCdd candidate = candidate();
        final Review pending = new Review();
        candidate.setReviews(new ArrayList<>(List.of(pending)));
        final ClassCdd next = new ClassCdd("Order", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(mockReviews.nextUnprocessedOfType(ClassCdd.class)).thenReturn(next);

        assertThat(classUnderTest.rejectAndNext(candidate, ReviewRationale.NOT_RELEVANT)).isSameAs(next);
        verify(mockReviews).reject(pending, ReviewRationale.NOT_RELEVANT);
        verify(mockMessageService, never()).informUser(any());
    }

    @Test
    void rejectAndNext_informsTheUserWhenTheQueueIsExhausted() {
        final ClassCdd candidate = candidate();
        candidate.setReviews(new ArrayList<>(List.of(new Review())));
        when(mockReviews.nextUnprocessedOfType(ClassCdd.class)).thenReturn(null);

        assertThat(classUnderTest.rejectAndNext(candidate, ReviewRationale.DUPLICATE)).isNull();
        verify(mockMessageService).informUser("All candidates of this type have been reviewed. Nothing left to review.");
    }

    // --- helpers ---

    private static ClassCdd candidate() {
        return new ClassCdd("Customer", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static Review review(ReviewStatus status) {
        final Review review = new Review();
        review.setStatus(status);
        return review;
    }
}
