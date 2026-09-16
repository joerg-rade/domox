package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByCandidateId(Long candidateId);

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