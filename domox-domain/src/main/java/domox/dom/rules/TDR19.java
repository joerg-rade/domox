package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 19)
public class TDR19 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nsubjpass(VBN, E1) and nmod:to(VBN, E2)
        return currentTd.nsubjpass() && nextTd != null && nextTd.nmodTo();
    }

    @Then
    public void then() {
        // relationship.add(E1 (VBN + "to") E2)
        String vbn = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + vbn + " to) " + e2 + ")";
    }

}

