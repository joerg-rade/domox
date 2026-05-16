package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 29)
public class TDR29 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        // if A=VB AND A in {get, send, prepare}
        if (currentTd.nsubj() || currentTd.nsubjpass() || currentTd.dobj() ||
            currentTd.iobj() || currentTd.pobj() || currentTd.nmodTo() || currentTd.mark()) {
            if (currentTd.isVerbA() && isActionVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // if B='system' -> Output_Data, else -> Input_Data
        String b = currentTd.getB();
        if (currentTd.isBasicAttributeB()) {
            if (b.equalsIgnoreCase("system")) {
                result = "Output_Data.add(" + b + ")";
            } else {
                result = "Input_Data.add(" + b + ")";
            }
        }
    }

    private boolean isActionVerb(String verb) {
        return verb.equalsIgnoreCase("get") ||
               verb.equalsIgnoreCase("send") ||
               verb.equalsIgnoreCase("prepare");
    }

}

