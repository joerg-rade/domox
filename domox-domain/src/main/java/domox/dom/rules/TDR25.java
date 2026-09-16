package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.isNounA;
import static domox.dom.nlp.TypedDependencyPredicates.nummod;

@RuleBean
@Rule(order = 25)
public class TDR25 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nummod(E1, CD) -> multiplicity.add(E1, CD)
        // E1 (governor) must be a noun entity
        return nummod(currentTd) && isNounA(currentTd);
    }

    @Override
    @Then
    public void then() {
        // multiplicity.add(E1, CD)
        String e1 = currentTd.getA();
        String cd = currentTd.getB();
        result = "multiplicity.add(" + e1 + ", " + cd + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        // The CD (number) is a multiplicity value, not a separate class
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    null,
                    null,
                    result);
        }
    }

}