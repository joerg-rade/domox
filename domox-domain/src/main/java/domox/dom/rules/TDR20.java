package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 20)
public class TDR20 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nsubj(Verb, E1) and nsubjpass(VBN, E2) and nmod:to(VBN, E3)
        // Complex pattern needing multiple dependency access
        if (currentTd.nsubj() && nextTd != null) {
            // Simplified: checking if next is available
            return nextTd.nsubjpass() || nextTd.nmodTo();
        }
        return false;
    }

    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E1 (VBN + "to") E3), relationship.add(E2 (VBN + "to") E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        result = "relationship.add(" + e1 + " (" + verb + ") E2), " +
                 "relationship.add(" + e1 + " (VBN to) E3), " +
                 "relationship.add(E2 (VBN to) E3)";
    }

}

