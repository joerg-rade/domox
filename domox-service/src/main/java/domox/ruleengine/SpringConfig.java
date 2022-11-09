package domox.ruleengine;

import com.deliveredtechnologies.rulebook.model.RuleBook;
import com.deliveredtechnologies.rulebook.spring.SpringAwareRuleBookRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("domox.ruleengine")
public class SpringConfig {
    @Bean
    public RuleBook ruleBook() {
        RuleBook ruleBook = new SpringAwareRuleBookRunner("domox.ruleengine");
        return ruleBook;
    }
}