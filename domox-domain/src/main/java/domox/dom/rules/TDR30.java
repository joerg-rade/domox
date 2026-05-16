package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 30)
public class TDR30 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nmod:by(A,B) OR nmod:agent(A,B) OR nmod:with(A,B)
        // if A=VB and A in {inputted, entered, filled, clicked, selected, added, recorded, processed, validateed}
        if (currentTd.nmodBy() || currentTd.nmodAgent() || currentTd.nmodWith()) {
            if (currentTd.isVerbA() && isInputPastVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // while (TD≠ nmod:by || nmod:agent || nmod:with)
        //   if(TD.B==attributes) Input_Data.add(B)
        String b = currentTd.getB();
        if (currentTd.isBasicAttributeB()) {
            result = "Input_Data.add(" + b + ")";
        } else {
            result = "// Input by: " + b;
        }
    }

    private boolean isInputPastVerb(String verb) {
        return verb.equalsIgnoreCase("inputted") ||
               verb.equalsIgnoreCase("entered") ||
               verb.equalsIgnoreCase("filled") ||
               verb.equalsIgnoreCase("clicked") ||
               verb.equalsIgnoreCase("selected") ||
               verb.equalsIgnoreCase("added") ||
               verb.equalsIgnoreCase("recorded") ||
               verb.equalsIgnoreCase("processed") ||
               verb.equalsIgnoreCase("validated");
    }

}

