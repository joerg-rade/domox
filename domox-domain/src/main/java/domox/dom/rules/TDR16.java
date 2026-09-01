package domox.dom.rules;


import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 16)
public class TDR16 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Spec: nmod:of(E1, E2), where E1 and E2 are entities (nouns)
        return nmodOf(currentTd)
                && isNounA(currentTd)
                && isNounB(currentTd);
    }

    @Override
    @Then
    public void then() {
        // relationship.add(E1 (has) E2)
        String e1 = currentTd.getA();
        String e2 = currentTd.getB();
        result = "relationship.add(" + e1 + " (has) " + e2 + ")";

        // Phase 1: record the match; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ClassCdd",
                    capitalizeFirstLetter(e1),
                    "ClassCdd",
                    capitalizeFirstLetter(e2),
                    result);
        }
    }

}