package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

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
        // Spec: amod(E1, JJ) -> descriptor.add(E1, JJ)
        // E1 (governor) must be a noun entity, JJ (dependent) an adjective
        return amod(currentTd) && isNounA(currentTd) && isAdjectiveB(currentTd);
    }

    @Override
    @Then
    public void then() {
        // descriptor.add(E1, JJ)
        String e1 = currentTd.getA();
        String jj = currentTd.getB();
        result = "descriptor.add(" + e1 + ", " + jj + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        // The JJ (adjective) is a descriptor of entity E1, not a separate class
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