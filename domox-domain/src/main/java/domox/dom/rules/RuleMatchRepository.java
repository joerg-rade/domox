package domox.dom.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleMatchRepository extends JpaRepository<RuleMatch, Long> {

    List<RuleMatch> findByRuleClassName(final String ruleClassName);

}