package domox.dom.crc.service;
import domox.dom.AbstractEntity;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.Review;
import domox.dom.crc.model.ReviewRationale;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.crc.repository.ReviewRepository;
import domox.dom.rules.RuleMatch;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.apache.causeway.applib.services.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    void create_attachesReviewToCandidateWithUserAndTimestamp() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        when(mockUserService.currentUserNameElseNobody()).thenReturn("testUser");

        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.create(candidate);

        // then
        assertThat(result.getCandidate()).isEqualTo(candidate);
        assertThat(result.getUser()).isEqualTo("testUser");
        assertThat(result.getCreatedAt()).as("createdAt must be set").isNotNull();
        assertThat(result.getStatus()).as("status must be null on creation").isNull();
        verify(mockRepositoryService).persist(review);
        assertThat(candidate.getReviews()).as("candidate must contain the new review").hasSize(1);
        assertThat(candidate.getReviews().getFirst()).isEqualTo(review);
    }

    @Test
    void approve_setsStatusApprovedAndClearsRationale() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));

        // when
        final Review result = classUnderTest.approve(review);

        // then
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.getRationale()).as("rationale should be null after approval").isNull();
        assertThat(result.getUpdatedAt()).as("updatedAt must be set").isNotNull();
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void reject_setsStatusRejectedWithRationale() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));

        // when
        final Review result = classUnderTest.reject(review, ReviewRationale.DUPLICATE);

        // then
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(result.getRationale()).isEqualTo(ReviewRationale.DUPLICATE);
        assertThat(result.getUpdatedAt()).as("updatedAt must be set").isNotNull();
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void resubmit_clearsStatusAndRationale() {
        // given
        final Review review = new Review();
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        review.setStatus(ReviewStatus.REJECTED);
        review.setRationale(ReviewRationale.NOT_RELEVANT);
        review.setUpdatedAt(Timestamp.valueOf("2026-01-02 00:00:00"));

        // when
        final Review result = classUnderTest.resubmit(review);

        // then
        assertThat(result.getStatus()).as("status must be cleared on resubmit").isNull();
        assertThat(result.getRationale()).as("rationale must be cleared on resubmit").isNull();
        assertThat(result.getUpdatedAt()).as("updatedAt must be updated").isNotNull();
        verify(mockRepositoryService).persist(review);
    }

    // --- Automated (LLM/MCP) review workflow ---

    @Test
    void findOrCreateReviewForAgent_createsReviewOwnedByAgentUser() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.findOrCreateReviewForAgent(candidate);

        // then
        assertThat(result.getCandidate()).isEqualTo(candidate);
        assertThat(result.getUser()).isEqualTo(Reviews.AGENT_USER);
        assertThat(result.getCreatedAt()).as("createdAt must be set").isNotNull();
        assertThat(result.getStatus()).as("status must be null on fresh agent review").isNull();
        verify(mockRepositoryService).persist(review);
        assertThat(candidate.getReviews()).as("candidate must contain the agent review").hasSize(1);
    }

    @Test
    void findOrCreateReviewForAgent_reusesExistingReview() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review first = classUnderTest.findOrCreateReviewForAgent(candidate);
        final Review second = classUnderTest.findOrCreateReviewForAgent(candidate);

        // then
        assertThat(second).as("second call must reuse the same review").isEqualTo(first);
        assertThat(candidate.getReviews()).as("only one review row must be created").hasSize(1);
        verify(mockRepositoryService, times(1)).persist(review);
    }

    @Test
    void approveAsAgent_reusesExistingAgentReview() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        final Review review = new Review();
        review.setUser(Reviews.AGENT_USER);
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        candidate.getReviews().add(review);

        // when
        final Review result = classUnderTest.approveAsAgent(candidate);

        // then
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.getUser()).isEqualTo(Reviews.AGENT_USER);
        assertThat(result.getUpdatedAt()).as("updatedAt must be set").isNotNull();
        verify(mockRepositoryService).persist(review);
        assertThat(candidate.getReviews()).as("no duplicate review row").hasSize(1);
    }

    @Test
    void approveAsAgent_createsReviewWhenMissing() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        final Review review = new Review();
        when(mockFactoryService.detachedEntity(Review.class)).thenReturn(review);

        // when
        final Review result = classUnderTest.approveAsAgent(candidate);

        // then
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.getUser()).isEqualTo(Reviews.AGENT_USER);
        assertThat(candidate.getReviews()).as("must create exactly one review").hasSize(1);
        verify(mockRepositoryService, atLeastOnce()).persist(review);
    }

    @Test
    void rejectAsAgent_reusesExistingAgentReview() {
        // given
        final AttributeCdd candidate = new AttributeCdd("speed", "int");
        final Review review = new Review();
        review.setUser(Reviews.AGENT_USER);
        review.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        candidate.getReviews().add(review);

        // when
        final Review result = classUnderTest.rejectAsAgent(candidate, ReviewRationale.DUPLICATE);

        // then
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(result.getRationale()).isEqualTo(ReviewRationale.DUPLICATE);
        assertThat(result.getUser()).isEqualTo(Reviews.AGENT_USER);
        assertThat(result.getUpdatedAt()).as("updatedAt must be set").isNotNull();
        verify(mockRepositoryService).persist(review);
    }

    @Test
    void findByUniqueId_returnsNullForUnknownId() {
        // given
        when(mockRepositoryService.allInstances(any())).thenReturn(List.of());

        // when / then
        assertThat(classUnderTest.findByUniqueId(123L)).as("unknown id must yield null").isNull();
    }

    // --- Workflow: next-unprocessed candidate (type-restricted) ---

    @Test
    void nextUnprocessedOfType_restrictsToTheGivenType() {
        // given: a lower-priority ClassCdd and a higher-priority AttributeCdd; when restricted
        // to ClassCdd the AttributeCdd must be ignored even though it would win the ordering
        final ClassCdd processedClass = new ClassCdd("Customer", List.of(), List.of(), List.of());
        setId(processedClass, 5L);
        final ClassCdd unprocessedClass = new ClassCdd("Order", List.of(), List.of(), List.of());
        setId(unprocessedClass, 20L);
        final AttributeCdd highPriorityProperty = new AttributeCdd("total", "double");
        highPriorityProperty.setRuleMatches(List.of(new RuleMatch(), new RuleMatch(), new RuleMatch()));
        setId(highPriorityProperty, 30L);

        when(mockReviewRepository.findProcessedCandidateIds()).thenReturn(List.of(5L));
        when(mockRepositoryService.allInstances(any())).thenReturn(List.of());
        when(mockRepositoryService.allInstances(ClassCdd.class)).thenReturn(List.of(processedClass, unprocessedClass));
        when(mockRepositoryService.allInstances(AttributeCdd.class)).thenReturn(List.of(highPriorityProperty));

        // when
        final Candidate result = classUnderTest.nextUnprocessedOfType(ClassCdd.class);

        // then: must pick the next ClassCdd, not the higher-priority AttributeCdd
        assertThat(result).as("must choose the next ClassCdd, never a different type").isEqualTo(unprocessedClass);
    }

    @Test
    void nextUnprocessed_considersEveryCandidateType() {
        // given: a lower-priority ClassCdd and a higher-priority AttributeCdd
        final ClassCdd cls = new ClassCdd("Order", List.of(), List.of(), List.of());
        setId(cls, 20L);
        final AttributeCdd highPriorityProperty = new AttributeCdd("total", "double");
        highPriorityProperty.setRuleMatches(List.of(new RuleMatch(), new RuleMatch()));
        setId(highPriorityProperty, 30L);

        when(mockReviewRepository.findProcessedCandidateIds()).thenReturn(List.of());
        when(mockRepositoryService.allInstances(any())).thenReturn(List.of());
        when(mockRepositoryService.allInstances(ClassCdd.class)).thenReturn(List.of(cls));
        when(mockRepositoryService.allInstances(AttributeCdd.class)).thenReturn(List.of(highPriorityProperty));

        // when (no type restriction, as used by the MCP pipeline)
        final Candidate result = classUnderTest.nextUnprocessed();

        // then: higher rule-match count across types must win
        assertThat(result).as("unrestricted lookup must consider every type").isEqualTo(highPriorityProperty);
    }

    /** Assigns the private JPA {@code id} field (normally set by the sequence generator). */
    private static void setId(final Candidate candidate, final long id) {
        try {
            final Field field = AbstractEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(candidate, id);
        } catch (final Exception e) {
            throw new RuntimeException("failed to set id on " + candidate, e);
        }
    }
}