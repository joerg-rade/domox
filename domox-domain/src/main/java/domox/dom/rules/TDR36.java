package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 36)
public class TDR36 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) and A="validate"
        if (currentTd.nsubj()) {
            String a = currentTd.getA();
            return a != null && a.equalsIgnoreCase("validate");
        }
        return false;
    }

    @Then
    public void then() {
        // System_Actions.add(B + A)
        // while (TD≠NULL) if(TD.B==attributes) System_Actions.add(B)
        String a = currentTd.getA();
        String b = currentTd.getB();

        if (b != null) {
            result = "System_Actions.add(" + b + " " + a + ")";

            // If B is a basic attribute, also add it to System_Actions
            if (currentTd.isBasicAttributeB()) {
                result += "; System_Actions.add(" + b + ")";
            }
        }
    }

}

