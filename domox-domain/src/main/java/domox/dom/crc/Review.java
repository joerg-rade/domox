package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Named;
import jakarta.persistence.*;
import lombok.*;
import org.apache.causeway.applib.annotation.*;
import org.jspecify.annotations.NonNull;

import java.sql.Timestamp;

@Entity
@Table(schema = DomainModule.SCHEMA)
@Named(DomainModule.NAMESPACE + ".Review")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "check-circle",
        describedAs = "A human review of a candidate")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class Review extends AbstractEntity implements Comparable<Review> {

    @Title
    public String title() {
        return "Review of " +
                (candidate != null ? candidate.getCandidateName() : "?") +
                " (" + (status != null ? status : "PENDING") + ")";
    }

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    @Getter
    @Setter
    @ToString.Exclude
    private Candidate candidate;

    @Column
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private ReviewStatus status;

    @Column
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private ReviewRationale rationale;

    @Column(nullable = false)
    @Getter
    @Setter
    private String user;

    @Column(nullable = false)
    @Getter
    @Setter
    private Timestamp createdAt;

    @Column
    @Getter
    @Setter
    private Timestamp updatedAt;

    @Override
    public int compareTo(@NonNull Review o) {
        return Long.compare(this.getId(), o.getId());
    }
}