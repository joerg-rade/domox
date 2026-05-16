package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 23)
public class TDR23 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nmod:as(VB, E1) and dobj(VB, E2)
        return currentTd.nmodAs() && nextTd != null && nextTd.dobj();
    }

    @Then
    public void then() {
        // relationship.add(E1 (VB) E2)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + ")";
    }

}

