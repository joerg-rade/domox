package domox.ruleengine;

import com.deliveredtechnologies.rulebook.FactMap;
import com.deliveredtechnologies.rulebook.model.runner.RuleBookRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class HelloSpringRuleTest {

    @Test
    public void ruleBookRunnerResultIsNotPresentIfNull() {
        RuleBookRunner ruleBookRunner =
                new RuleBookRunner("com.deliveredtechnologies.rulebook.model.runner.noresult");
        FactMap<String> facts = new FactMap<>();
        facts.setValue("hello", "Hello");
        facts.setValue("world", "World");

        ruleBookRunner.run(facts);

        assertFalse(ruleBookRunner.getResult().isPresent());
    }
}
