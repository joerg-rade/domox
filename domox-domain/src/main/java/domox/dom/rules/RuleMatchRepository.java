package domox.dom.rules;

import domox.dom.nlp.Sentence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleMatchRepository extends JpaRepository<RuleMatch, Long> {

    List<RuleMatch> findByRuleClassName(final String ruleClassName);

    List<RuleMatch> findByTypedDependency_Sentence(final Sentence sentence);

}