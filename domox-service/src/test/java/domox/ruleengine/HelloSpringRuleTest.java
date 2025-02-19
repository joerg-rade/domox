package domox.ruleengine;

import com.deliveredtechnologies.rulebook.FactMap;
import com.deliveredtechnologies.rulebook.NameValueReferableMap;
import com.deliveredtechnologies.rulebook.model.RuleBook;
import lombok.NoArgsConstructor;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ExtendWith(value = SpringExtension.class)
@NoArgsConstructor
public class HelloSpringRuleTest {

    // @Inject
    public RuleBook ruleBook;

    @Test
    public void test() {
        NameValueReferableMap<String> facts = new FactMap<>();
        facts.setValue("hello", "Hello ");
        facts.setValue("world", "World");
        ruleBook.run(facts);
        ruleBook.getResult().ifPresent(System.out::println); //prints Hello World!
    }
}
