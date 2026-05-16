package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 7)
public class TDR7 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return currentTd.nmodIn() && currentTd.isNounA() && currentTd.isNounB();
    }

    @Then
    public void then() {
        // if A=Noun and B=Noun then
        // Entity.add(B), Attributes.add(A)
        result = "Entity.add(" + currentTd.getB() + "), Attributes.add(" + currentTd.getA() + ")";
    }

}

