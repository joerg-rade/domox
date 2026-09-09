package domox.dom.uml;

import domox.dom.AbstractEntity;
import domox.dom.rules.RuleMatch;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;

/**
 * Base class for all UML class-diagram candidate entities.
 * <p>
 * Holds the sentence / typed-dependency provenance of a candidate, and passes on
 * the {@link AbstractEntity} {@code id}/{@code version} mapping to its concrete
 * {@code @Entity} subclasses via the {@link MappedSuperclass} inheritance chain.
 */
@Data
@MappedSuperclass
public abstract class Candidate extends AbstractEntity {

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateName;

    @Column(nullable = false)
    @Getter
    @Setter
    private String candidateType;

    @Getter
    @Setter
    private List<RuleMatch> ruleMatches;

    /**
     * Adds a matching rule to this candidate.
     *
     * @param match The rule match to add.
     */
    @Programmatic
    public void addMatchingRule(RuleMatch match) {
        if (match != null && !ruleMatches.contains(match)) {
            ruleMatches.add(match);
        }
    }
}