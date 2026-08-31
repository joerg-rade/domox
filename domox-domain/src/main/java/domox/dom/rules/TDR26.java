package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 26)
public class TDR26 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: det(E1, DT) -> cardinalities.add based on the DT value
        // E1 (governor) must be a noun entity and DT (dependent) one of
        // the determiners handled by this rule
        if (!currentTd.det() || !currentTd.isNounA() || currentTd.getB() == null) {
            return false;
        }
        String dt = currentTd.getB().toLowerCase();
        return isMultiplicityDeterminer(dt) || dt.equals("a") || dt.equals("an");
    }

    @Override
    @Then
    public void then() {
        // if (DT="Each" OR "All" OR "some" OR "Any" OR "Many" OR "Every" OR "multiple")
        //    cardinalities.add(E1 ">" N)
        // if (DT= "a" OR "an")
        //    cardinalities.add(E1 ">" 1)
        String e1 = currentTd.getA();
        String dt = currentTd.getB().toLowerCase();

        if (isMultiplicityDeterminer(dt)) {
            result = "cardinalities.add(" + e1 + " > N)";
        } else {
            result = "cardinalities.add(" + e1 + " > 1)";
        }

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(dt),
                    result);
        }
    }

    private boolean isMultiplicityDeterminer(String dt) {
        return dt.equals("each") || dt.equals("all") || dt.equals("some") ||
                dt.equals("any") || dt.equals("many") || dt.equals("every") ||
                dt.equals("multiple");
    }

}