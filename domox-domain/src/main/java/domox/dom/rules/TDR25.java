package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 25)
public class TDR25 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if dependency = nummod(E1, CD)
        return currentTd.nummod();
    }

    @Then
    public void then() {
        // cardinalities.add(E1 ">" CD)
        String e1 = currentTd.getA();
        String cd = currentTd.getB();
        result = "cardinalities.add(" + e1 + " > " + cd + ")";
    }

}

