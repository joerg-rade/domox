package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 8)
public class TDR8 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependency= nmod:to(A,B) OR nmod:for(A,B) OR nmod:from(A,B) OR nmod:as(A,B)
        // if B=Noun then
        return (currentTd.nmodTo() || currentTd.nmodFor() || currentTd.nmodFrom() || currentTd.nmodAs())
                && currentTd.isNounB();
    }

    @Then
    public void then() {
        // Entity.add(B)
        String b = currentTd.getB();
        result = "Entity.add(" + b + ")";
    }

}

