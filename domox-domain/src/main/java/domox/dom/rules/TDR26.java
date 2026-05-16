package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 26)
public class TDR26 extends TypedDependencyRuleWithPreviousAndNext {

    @Result
    private String result;

    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // if dependency = det(E1, DT)
        return currentTd.det();
    }

    @Then
    public void then() {
        // if (DT="Each" OR "All" OR "some" OR "Any" OR "Many" OR "Every" OR "multiple")
        //    cardinalities.add(E1 ">" N)
        // if (DT= "a" OR "an")
        //    cardinalities.add(E1 ">" 1)
        String e1 = currentTd.getA();
        String dt = currentTd.getB().toLowerCase();

        if (dt.equals("each") || dt.equals("all") || dt.equals("some") ||
            dt.equals("any") || dt.equals("many") || dt.equals("every") || dt.equals("multiple")) {
            result = "cardinalities.add(" + e1 + " > N)";
        } else if (dt.equals("a") || dt.equals("an")) {
            result = "cardinalities.add(" + e1 + " > 1)";
        }
    }

}

