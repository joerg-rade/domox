package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 35)
public class TDR35 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= (advcl:if(A,B) OR mark(A,if))
        // Simplified: checking if we have advcl or mark with "if"
        if (currentTd.advcl() || currentTd.mark()) {
            String b = currentTd.getB();
            if (b.equalsIgnoreCase("if")) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // System_Actions.add("if" + dobj.B + dobj.A)
        // while (TD≠advmod)
        //   if(TD.B==attributes) System_Actions.add(B)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "System_Actions.add(\"if \" + " + b + " + \" \" + " + a + ")";
    }

}

