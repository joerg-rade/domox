package domox.ruleengine;

import com.deliveredtechnologies.rulebook.annotation.Given;
import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 1)
public class HelloSpringRule {
    @Given("hello")
    private String hello;

    @Result
    private String result;

    @When
    public boolean when() {
        return hello != null;
    }

    @Then
    public void then() {
        result = hello;
    }
}