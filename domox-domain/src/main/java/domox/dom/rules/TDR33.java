package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 33)
public class TDR33 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= nsubj(A,B) OR nmod:by(A,B)
        // if A=VB and A in {receive, accept, get, obtain, acquire, redeem}
        if (currentTd.nsubj() || currentTd.nmodBy()) {
            if (currentTd.isVerbA() && isReceiveVerb(currentTd.getA())) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // if B=System -> User_Action.add(A), else User_Action.add(A)
        String verb = currentTd.getA();
        result = "User_Action.add(" + verb + ")";
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

