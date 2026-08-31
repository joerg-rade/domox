package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 37)
public class TDR37 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }

        // Spec: Dependencies = nsubj(A,B) OR xcomp(A,B)
        //        if A in {continue, restart, go, repeat}
        if (currentTd.nsubj() || currentTd.xcomp()) {
            String a = currentTd.getA();
            return isControlFlowVerb(a);
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec: System_Action.add(A + nummod.B || dobj.B)
        // (falls back to currentTd's B when no nummod/dobj companion exists)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "System_Action.add(" + a + " " + b + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "System_Action",
                    capitalizeFirstLetter(a),
                    null,
                    null,
                    result);
        }
    }

    private boolean isControlFlowVerb(String verb) {
        return verb.equalsIgnoreCase("continue") ||
                verb.equalsIgnoreCase("restart") ||
                verb.equalsIgnoreCase("go") ||
                verb.equalsIgnoreCase("repeat");
    }

}