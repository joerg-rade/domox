package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 18)
public class TDR18 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nsubj(VB, E1) and dobj(VB, E2) and nmod:to(VB, E3)
        if (currentTd.nsubj() && nextTd != null && (nextTd.dobj() || nextTd.nmodTo())) {
            return true;
        }
        return false;
    }

    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E2 (VB+ "to") E3), relationship.add(E1 (VB+ "to") E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + "), " +
                 "relationship.add(" + e2 + " (" + verb + " to) E3), " +
                 "relationship.add(" + e1 + " (" + verb + " to) E3)";
    }

}

