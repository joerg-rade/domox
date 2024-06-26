package domox.ruleengine;

import com.deliveredtechnologies.rulebook.annotation.Given;
import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import org.springframework.beans.factory.annotation.Autowired;

@RuleBean
@Rule(order = 2)
public class WorldSpringRule {
    @Autowired
    HelloWorldComponent helloWorldComponent;

    @Given("world")
    private String world;

    @Result
    private String result;

    @When
    public boolean when() {
        return world != null;
    }

    @Then
    public void then() {
        result = helloWorldComponent.getHelloWorld(result, world);
    }
}