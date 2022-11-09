package domox.ruleengine;

import com.deliveredtechnologies.rulebook.FactMap;
import com.deliveredtechnologies.rulebook.NameValueReferableMap;
import com.deliveredtechnologies.rulebook.model.RuleBook;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {SpringConfig.class})
public class HelloSpringRuleTest {

    @Autowired
    private RuleBook ruleBook;

    @Test
    public void test() {
        NameValueReferableMap<String> facts = new FactMap<>();
        facts.setValue("hello", "Hello ");
        facts.setValue("world", "World");
        ruleBook.run(facts);
        ruleBook.getResult().ifPresent(System.out::println); //prints Hello World!
    }
}
