package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 34)
public class TDR34 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if Dependencies= xcomp(A,B) OR amod(A,B) OR neg(A,B)
        // if A || B in {error, fail, wrong, invalid, incorrect, not}
        if (currentTd.xcomp() || currentTd.amod() || currentTd.neg()) {
            String a = currentTd.getA();
            String b = currentTd.getB();
            if (isExceptionTerm(a) || isExceptionTerm(b)) {
                return true;
            }
        }
        return false;
    }

    @Then
    public void then() {
        // Exceptions.add(B + A)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "Exceptions.add(" + b + " " + a + ")";
    }

    private boolean isExceptionTerm(String term) {
        return term.equalsIgnoreCase("error") ||
               term.equalsIgnoreCase("fail") ||
               term.equalsIgnoreCase("wrong") ||
               term.equalsIgnoreCase("invalid") ||
               term.equalsIgnoreCase("incorrect") ||
               term.equalsIgnoreCase("not");
    }

}

