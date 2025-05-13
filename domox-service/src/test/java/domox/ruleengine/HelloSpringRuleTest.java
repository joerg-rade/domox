package domox.ruleengine;

import com.deliveredtechnologies.rulebook.FactMap;
import com.deliveredtechnologies.rulebook.model.runner.RuleBookRunner;
import org.junit.Assert;
import org.junit.Test;

public class HelloSpringRuleTest {

    @Test
    public void ruleBookRunnerResultIsNotPresentIfNull() {
        RuleBookRunner ruleBookRunner =
                new RuleBookRunner("com.deliveredtechnologies.rulebook.model.runner.noresult");
        FactMap<String> facts = new FactMap<>();
        facts.setValue("hello", "Hello");
        facts.setValue("world", "World");

        ruleBookRunner.run(facts);

        Assert.assertFalse(ruleBookRunner.getResult().isPresent());
    }
}
