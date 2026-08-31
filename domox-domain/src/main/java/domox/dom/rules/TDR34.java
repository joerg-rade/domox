package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 34)
public class TDR34 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: Dependencies = xcomp(A,B) OR amod(A,B) OR neg(A,B)
        //        if A || B in {error, fail, wrong, invalid, incorrect, not}
        if (currentTd.xcomp() || currentTd.amod() || currentTd.neg()) {
            String a = currentTd.getA();
            String b = currentTd.getB();
            return isExceptionTerm(a) || isExceptionTerm(b);
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        // Spec: Exceptions.add(B + A)
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "Exceptions.add(" + b + " " + a + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "Exceptions",
                    capitalizeFirstLetter(a != null ? a : b),
                    null,
                    null,
                    result);
        }
    }

    private boolean isExceptionTerm(String term) {
        return term != null && (
                term.equalsIgnoreCase("error") ||
                        term.equalsIgnoreCase("fail") ||
                        term.equalsIgnoreCase("wrong") ||
                        term.equalsIgnoreCase("invalid") ||
                        term.equalsIgnoreCase("incorrect") ||
                        term.equalsIgnoreCase("not"));
    }

}