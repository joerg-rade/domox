package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 2)
public class TDR2 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        boolean answer = false;
        if (currentTd.nsubj() || currentTd.nsubjpass()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && currentTd.isBasicAttributeB()) {
                answer = true;  // Rule fires regardless of previousTd.compound()
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd.compound()) {
            // Attributes.add(compound(B) + Compound(A))
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            // Attributes.add(nsubj(B))
            result = "nsubj(" + currentTd.getB() + ")";
        }
    }

}

