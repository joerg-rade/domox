package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 24)
public class TDR24 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if dependency = amod(E1, JJ)
        return currentTd.amod() && currentTd.isAdjectiveB();
    }

    @Then
    public void then() {
        // cardinalities.add(E1 ">" JJ)
        String e1 = currentTd.getA();
        String jj = currentTd.getB();
        result = "cardinalities.add(" + e1 + " > " + jj + ")";
    }

}

