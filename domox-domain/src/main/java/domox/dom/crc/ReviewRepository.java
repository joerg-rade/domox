package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Returns the reviews attached to the candidate with the given {@code id}.
     * <p>
     * Uses a native query for the same reason as {@link #findProcessedCandidateIds()}: the
     * obvious derived query ({@code findByCandidateId}) navigates {@code r.candidate.id},
     * which makes EclipseLink cross-join the non-existent {@code domox.Candidate} base table
     * ({@link Candidate} uses {@code TABLE_PER_CLASS}, so only concrete subclass tables exist),
     * failing with {@code column t0.id does not exist}.
     */
    @Query(value = "SELECT r.* FROM domox.Review r WHERE r.candidate_id = :candidateId", nativeQuery = true)
    List<Review> findByCandidateId(@Param("candidateId") Long candidateId);

    List<Review> findByStatus(ReviewStatus status);

    List<Review> findByUser(String user);

    /**
     * Returns IDs of candidates that have at least one Review with APPROVED or REJECTED status.
     * <p>
     * Uses a native query because {@link Candidate} uses {@link jakarta.persistence.InheritanceType#TABLE_PER_CLASS},
     * so no physical {@code domox.Candidate} table exists — only concrete subclass tables do.
     * A standard JPQL navigation ({@code r.candidate.id}) would cause EclipseLink to generate an
     * invalid join to the non-existent base table.
     */
    @Query(value = "SELECT DISTINCT r.candidate_id FROM domox.Review r WHERE r.status IN ('APPROVED', 'REJECTED')", nativeQuery = true)
    List<Long> findProcessedCandidateIds();
}