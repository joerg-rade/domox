package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 31)
public class TDR31 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nmod:by(A,B) OR nmod:agent(A,B) OR nmod:with(A,B)
        // if A=VB and A in {displayed, outputted, retrieved, showed, viewed, printed}
        if (currentTd.nmodBy() || currentTd.nmodAgent() || currentTd.nmodWith()) {
            if (currentTd.isVerbA() && isOutputPastVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // while (TD≠ nmod:by || nmod:agent || nmod:with)
        //   if(TD.B==attributes) Output_Data.add(B)
        String b = currentTd.getB();
        if (currentTd.isBasicAttributeB()) {
            result = "Output_Data.add(" + b + ")";
        } else {
            result = "// Output by: " + b;
        }
    }

    private boolean isOutputPastVerb(String verb) {
        return verb.equalsIgnoreCase("displayed") ||
               verb.equalsIgnoreCase("outputted") ||
               verb.equalsIgnoreCase("retrieved") ||
               verb.equalsIgnoreCase("showed") ||
               verb.equalsIgnoreCase("viewed") ||
               verb.equalsIgnoreCase("printed");
    }

}

