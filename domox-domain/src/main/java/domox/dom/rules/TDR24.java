package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

@RuleBean
@Rule(order = 24)
public class TDR24 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: amod(E1, JJ) -> cardinalities.add(E1 ">" JJ)
        // E1 (governor) must be a noun entity, JJ (dependent) an adjective
        return currentTd.amod() && currentTd.isNounA() && currentTd.isAdjectiveB();
    }

    @Override
    @Then
    public void then() {
        // cardinalities.add(E1 ">" JJ)
        String e1 = currentTd.getA();
        String jj = currentTd.getB();
        result = "cardinalities.add(" + e1 + " > " + jj + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(jj),
                    result);
        }
    }

}