package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 37)
public class TDR37 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }

        // if Dependencies= nsubj(A,B) OR xcomp(A,B)
        // if A in {continue, restart, go, repeat}
        if (currentTd.nsubj() || currentTd.xcomp()) {
            String a = currentTd.getA();
            if (isControlFlowVerb(a)) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // System_Action.add(A + nummod.B || dobj.B)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "System_Action.add(" + a + " " + b + ")";
    }

    private boolean isControlFlowVerb(String verb) {
        return verb.equalsIgnoreCase("continue") ||
               verb.equalsIgnoreCase("restart") ||
               verb.equalsIgnoreCase("go") ||
               verb.equalsIgnoreCase("repeat");
    }

}

