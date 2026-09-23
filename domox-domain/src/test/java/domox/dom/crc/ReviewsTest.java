package domox.dom.crc;

import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertNotNull;
import static org.apache.causeway.commons.internal.assertions._Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewsTest {

    private Reviews classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @Mock
    FactoryService mockFactoryService;

    @Mock
    ReviewRepository mockReviewRepository;

    @Mock
    UserService mockUserService;

    @BeforeEach
    public void setUp() {
        classUnderTest = new Reviews(
                mockRepositoryService,
                mockFactoryService,
                mockReviewRepository,
                mockUserService);
    }

    @Test
    void testCreate() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        when(mockUserService.currentUserNameElseNobody()).thenReturn("testUser");

        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.create(candidate);

        // then
        assertEquals(candidate, result.getCandidate());
        assertEquals("testUser", result.getUser());
        assertNotNull(result.getCreatedAt(), "createdAt must be set");
        assertNull(result.getStatus(), "status must be null on creation");
        verify(mockRepositoryService).persist(review);
        assertEquals(1, candidate.getReviews().size(), "candidate must contain the new review");
        assertEquals(review, candidate.getReviews().getFirst());
    }

    @Test
    void testApprove() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));

        // when
        final Review result = classUnderTest.approve(review);

        // then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertNull(result.getRationale(), "rationale should be null after approval");
        assertNotNull(result.getUpdatedAt(), "updatedAt must be set");
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void testReject() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));

        // when
        final Review result = classUnderTest.reject(review, ReviewRationale.DUPLICATE);

        // then
        assertEquals(ReviewStatus.REJECTED, result.getStatus());
        assertEquals(ReviewRationale.DUPLICATE, result.getRationale());
        assertNotNull(result.getUpdatedAt(), "updatedAt must be set");
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void testResubmit() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        review.setStatus(ReviewStatus.REJECTED);
        review.setRationale(ReviewRationale.NOT_RELEVANT);
        review.setUpdatedAt(Timestamp.valueOf("2026-01-02 00:00:00"));

        // when
        final Review result = classUnderTest.resubmit(review);

        // then
        assertNull(result.getStatus(), "status must be cleared on resubmit");
        assertNull(result.getRationale(), "rationale must be cleared on resubmit");
        assertNotNull(result.getUpdatedAt(), "updatedAt must be updated");
        verify(mockRepositoryService).persist(review);
    }

    // --- Automated (LLM/MCP) review workflow ---

    @Test
    void testFindOrCreateReviewForAgent_createsWithAgentUser() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.findOrCreateReviewForAgent(candidate);

        // then
        assertEquals(candidate, result.getCandidate());
        assertEquals(Reviews.AGENT_USER, result.getUser());
        assertNotNull(result.getCreatedAt(), "createdAt must be set");
        assertNull(result.getStatus(), "status must be null on fresh agent review");
        verify(mockRepositoryService).persist(review);
        assertEquals(1, candidate.getReviews().size(), "candidate must contain the agent review");
    }

    @Test
    void testFindOrCreateReviewForAgent_isIdempotent() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review first = classUnderTest.findOrCreateReviewForAgent(candidate);
        final Review second = classUnderTest.findOrCreateReviewForAgent(candidate);

        // then
        assertEquals(first, second, "second call must reuse the same review");
        assertEquals(1, candidate.getReviews().size(), "only one review row must be created");
        verify(mockRepositoryService, times(1)).persist(review);
    }

    @Test
    void testApproveAsAgent_reusesExistingAgentReview() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        final Review review = new Review();
        review.setUser(Reviews.AGENT_USER);
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        candidate.getReviews().add(review);

        // when
        final Review result = classUnderTest.approveAsAgent(candidate);

        // then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(Reviews.AGENT_USER, result.getUser());
        assertNotNull(result.getUpdatedAt(), "updatedAt must be set");
        verify(mockRepositoryService).persist(review);
        assertEquals(1, candidate.getReviews().size(), "no duplicate review row");
    }

    @Test
    void testApproveAsAgent_createsReviewIfMissing() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.approveAsAgent(candidate);

        // then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(Reviews.AGENT_USER, result.getUser());
        assertEquals(1, candidate.getReviews().size(), "must create exactly one review");
        verify(mockRepositoryService, atLeastOnce()).persist(review);
    }

    @Test
    void testRejectAsAgent_reusesExistingAgentReview() {
        // given
        final PropertyCdd candidate = new PropertyCdd("speed", "int");
        final Review review = new Review();
        review.setUser(Reviews.AGENT_USER);
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        candidate.getReviews().add(review);

        // when
        final Review result = classUnderTest.rejectAsAgent(candidate, ReviewRationale.DUPLICATE);

        // then
        assertEquals(ReviewStatus.REJECTED, result.getStatus());
        assertEquals(ReviewRationale.DUPLICATE, result.getRationale());
        assertEquals(Reviews.AGENT_USER, result.getUser());
        assertNotNull(result.getUpdatedAt(), "updatedAt must be set");
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void testFindByUniqueId_noMatch_returnsNull() {
        // given
        when(mockRepositoryService.allInstances(any())).thenReturn(List.of());

        // when / then
        assertNull(classUnderTest.findByUniqueId(123L), "unknown id must yield null");
    }
}