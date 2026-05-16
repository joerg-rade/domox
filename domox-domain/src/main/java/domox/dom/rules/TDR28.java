package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 28)
public class TDR28 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        // if A=VB and A in {display, output, retrieve, show, view, print}
        if (currentTd.nsubj() || currentTd.nsubjpass() || currentTd.dobj() ||
            currentTd.iobj() || currentTd.pobj() || currentTd.nmodTo() || currentTd.mark()) {
            if (currentTd.isVerbA() && isOutputVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // while (TD≠nsubj || nsubjpass || dobj || iobj || pobj || mark)
        //   if(TD.B==attributes) Output_Data.add(B)
        String b = currentTd.getB();
        if (currentTd.isBasicAttributeB()) {
            result = "Output_Data.add(" + b + ")";
        } else {
            result = "// Output value: " + b;
        }
    }

    private boolean isOutputVerb(String verb) {
        return verb.equalsIgnoreCase("display") ||
               verb.equalsIgnoreCase("output") ||
               verb.equalsIgnoreCase("retrieve") ||
               verb.equalsIgnoreCase("show") ||
               verb.equalsIgnoreCase("view") ||
               verb.equalsIgnoreCase("print");
    }

}

