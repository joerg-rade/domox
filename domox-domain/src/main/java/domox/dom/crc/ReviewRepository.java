package domox.dom.crc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByCandidate(Candidate candidate);

    List<Review> findByStatus(ReviewStatus status);

    List<Review> findByUser(String user);
}