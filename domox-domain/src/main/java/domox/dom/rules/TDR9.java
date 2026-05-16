package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 9)
public class TDR9 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        return (currentTd.nmodBy() || currentTd.nmodAgent() || currentTd.nmodWith())
               && currentTd.isNounB();
    }

    @Then
    public void then() {
        if (currentTd.isBasicAttributeB()) {
            // if B=Noun and B = Basic_Attrib then Attributes.add(B)
            result = "Attributes.add(" + currentTd.getB() + ")";
        } else {
            // else if B=Noun and B ≠ Basic_Attrib then Entity.add(B)
            result = "Entity.add(" + currentTd.getB() + ")";
        }
    }

}

