package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 16)
public class TDR16 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nmod:of(E1, E2)
        return currentTd.nmodOf();
    }

    @Then
    public void then() {
        // relationship.add(E1 (has) E2)
        String e1 = currentTd.getA();
        String e2 = currentTd.getB();
        result = "relationship.add(" + e1 + " (has) " + e2 + ")";
    }

}

