package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 33)
public class TDR33 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getA() == null) {
            return false;
        }
        // Spec: Dependencies = nsubj(A,B) OR nmod:by(A,B)
        //        if A=VB and A in {receive, accept, get, obtain, acquire, redeem}
        if (currentTd.nsubj() || currentTd.nmodBy()) {
            return currentTd.isVerbA() && isReceiveVerb(currentTd.getA());
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec: if B=System -> User_Action.add(A), else User_Action.add(A)
        // (both branches are the same, so B does not affect the outcome)
        String verb = currentTd.getA();
        result = "User_Action.add(" + verb + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "User_Action",
                    capitalizeFirstLetter(verb),
                    null,
                    null,
                    result);
        }
    }

    private boolean isReceiveVerb(String verb) {
        return verb.equalsIgnoreCase("receive") ||
                verb.equalsIgnoreCase("accept") ||
                verb.equalsIgnoreCase("get") ||
                verb.equalsIgnoreCase("obtain") ||
                verb.equalsIgnoreCase("acquire") ||
                verb.equalsIgnoreCase("redeem");
    }

}