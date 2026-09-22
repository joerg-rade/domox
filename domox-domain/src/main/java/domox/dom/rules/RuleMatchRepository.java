package domox.dom.rules;

import domox.dom.nlp.Sentence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleMatchRepository extends JpaRepository<RuleMatch, Long> {

    List<RuleMatch> findByRuleClassName(final String ruleClassName);

    List<RuleMatch> findByTypedDependency_Sentence(final Sentence sentence);

    /**
     * Returns RuleMatches sharing the same candidate + rule signature
     * ({@code candidateName}, {@code candidateType}, {@code ruleClassName},
     * {@code description}, {@code relatedCandidateName},
     * {@code relatedCandidateType}).  Used to prevent persisting duplicate
     * matches when the same TDR rule fires repeatedly for the same candidate.
     */
    List<RuleMatch> findByCandidateNameAndCandidateTypeAndRuleClassNameAndDescriptionAndRelatedCandidateNameAndRelatedCandidateType(
            String candidateName,
            String candidateType,
            String ruleClassName,
            String description,
            String relatedCandidateName,
            String relatedCandidateType);

}