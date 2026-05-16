package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 27)
public class TDR27 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) OR nsubjpass(A,B) OR dobj(A,B) OR iobj(A,B) OR pobj(A,B) OR nmod:to(A,B) OR mark(A,B)
        // if A=VB and A in {input, enter, fill, click, select, add, record, process, validate}
        if (currentTd.nsubj() || currentTd.nsubjpass() || currentTd.dobj() ||
            currentTd.iobj() || currentTd.pobj() || currentTd.nmodTo() || currentTd.mark()) {
            if (currentTd.isVerbA() && isInputVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // while (TD≠nsubj || nsubjpass || dobj || iobj || pobj || mark)
        //   if(TD.B==attributes) Input_Data.add(B)
        String b = currentTd.getB();
        if (currentTd.isBasicAttributeB()) {
            result = "Input_Data.add(" + b + ")";
        } else {
            result = "// Input value: " + b;
        }
    }

    private boolean isInputVerb(String verb) {
        return verb.equalsIgnoreCase("input") ||
               verb.equalsIgnoreCase("enter") ||
               verb.equalsIgnoreCase("fill") ||
               verb.equalsIgnoreCase("click") ||
               verb.equalsIgnoreCase("select") ||
               verb.equalsIgnoreCase("add") ||
               verb.equalsIgnoreCase("record") ||
               verb.equalsIgnoreCase("process") ||
               verb.equalsIgnoreCase("validate");
    }

}

