package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 17)
public class TDR17 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // nsubj(Verb, E1) and dobj(VBN, E2) and nmod:of(E2, E3)
        // This is a complex pattern - for now, checking if we have nsubj and next has dobj
        if (currentTd.nsubj() && nextTd != null && nextTd.dobj()) {
            // Further checking would need access to the dependency sequence
            return true;
        }
        return false;
    }

    @Then
    public void then() {
        // relationship.add(E1 (VB) E2), relationship.add(E2 (has) E3)
        String verb = currentTd.getA();
        String e1 = currentTd.getB();
        String e2 = nextTd.getB();
        result = "relationship.add(" + e1 + " (" + verb + ") " + e2 + "), relationship.add(" + e2 + " (has) E3)";
    }

}

